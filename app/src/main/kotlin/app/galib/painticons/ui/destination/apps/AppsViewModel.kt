/*
 * Copyright 2026 Lawnchair Launcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.galib.painticons.ui.destination.apps

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.galib.painticons.data.model.Component
import app.galib.painticons.data.model.IconInfo
import app.galib.painticons.data.model.IconInfoModel
import app.galib.painticons.data.model.IconShape
import app.galib.painticons.data.model.InstalledApp
import app.galib.painticons.data.model.PaintedIconSettings
import app.galib.painticons.data.model.SearchMode
import app.galib.painticons.data.repository.IconPackExportRepository
import app.galib.painticons.data.repository.InstalledAppsRepository
import app.galib.painticons.data.repository.PreferenceManager
import app.galib.painticons.data.repository.home.IconRepository
import app.galib.painticons.data.util.decodeSelectedColors
import app.galib.painticons.data.util.decodeSelectedIcons
import app.galib.painticons.data.util.encodeSelectedColors
import app.galib.painticons.data.util.encodeSelectedIcons
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The state of the export of the painted icons as an icon pack.
 */
sealed interface IconPackExportState {
    data object Idle : IconPackExportState
    data object Building : IconPackExportState

    /** The icon pack was saved, with the location of the file. */
    data class Saved(val location: String) : IconPackExportState

    /** The icon pack could not be built, with the reason. */
    data class Failed(val reason: String) : IconPackExportState
}

@ViewModelKey
@ContributesIntoMap(AppScope::class)
class AppsViewModel(
    installedAppsRepository: InstalledAppsRepository,
    private val iconRepository: IconRepository,
    private val iconPackExportRepository: IconPackExportRepository,
    private val preferenceManager: PreferenceManager,
) : ViewModel() {
    private val _searchMode = MutableStateFlow(SearchMode.LABEL)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    val searchTermTextState = TextFieldState()

    val searchResults: StateFlow<IconInfoModel> = iconRepository.searchedIconInfoModel

    /** All icons of the pack, which the user can pick from for the apps. */
    val availableIcons: StateFlow<IconInfoModel> = iconRepository.iconInfoModel

    /**
     * Searches the icons for [query] in the current [searchMode].
     */
    fun searchIcons(query: String) {
        viewModelScope.launch {
            iconRepository.search(_searchMode.value, query)
        }
    }

    /**
     * Uses [mode] for the search and repeats the current search with it.
     */
    fun changeMode(mode: SearchMode) {
        if (_searchMode.value == mode) return
        _searchMode.value = mode
        searchIcons(searchTermTextState.text.toString())
    }

    /**
     * The icons picked by the user for apps, keyed by the package name of the app. The picks
     * are restored from the preferences, so that they are remembered across restarts of the
     * app.
     */
    private val _selectedIconNames = MutableStateFlow(
        decodeSelectedIcons(preferenceManager.selectedIconNames.get()),
    )
    val selectedIconNames: StateFlow<Map<String, String>> = _selectedIconNames.asStateFlow()

    /**
     * Every installed app, paired with the icon of the pack if it has one. Apps without an
     * icon are listed too, so that an icon can be picked for them.
     */
    val installedApps: StateFlow<List<InstalledApp>> = combine(
        installedAppsRepository.installedApps,
        iconRepository.iconInfoModel,
        _selectedIconNames,
    ) { installedApps, iconInfoModel, selectedIconNames ->
        // The apps are read faster than the pack's icons, and without them every app would
        // show its own icon for a moment before the painted one replaces it.
        if (iconInfoModel.iconInfo.isEmpty()) {
            emptyList()
        } else {
            installedApps.withPackIcons(iconInfoModel, selectedIconNames)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    /**
     * Pairs each app with the icon of the pack. An icon picked by the user wins over the one
     * the pack maps to the app, which is looked up by component first and by package second.
     */
    private fun List<InstalledApp>.withPackIcons(
        iconInfoModel: IconInfoModel,
        selectedIconNames: Map<String, String>,
    ): List<InstalledApp> {
        val iconsByName = mutableMapOf<String, IconInfo>()
        val iconsByComponent = mutableMapOf<Component, IconInfo>()
        val iconsByPackage = mutableMapOf<String, IconInfo>()
        iconInfoModel.iconInfo.forEach { iconInfo ->
            iconsByName.putIfAbsent(iconInfo.drawableName, iconInfo)
            iconInfo.componentNames.forEach { componentName ->
                val component = componentName.component
                iconsByComponent.putIfAbsent(component, iconInfo)
                iconsByPackage.putIfAbsent(component.packageName, iconInfo)
            }
        }

        return map { app ->
            app.copy(
                iconInfo = selectedIconNames[app.packageName]?.let(iconsByName::get)
                    ?: iconsByComponent[Component(app.packageName, app.className)]
                    ?: iconsByPackage[app.packageName],
            )
        }
    }

    /**
     * The colours picked by the user for apps, keyed by the package name of the app.
     * The picks are restored from the preferences, so that they are remembered across restarts
     * of the app.
     */
    private val _selectedColors = MutableStateFlow<Map<String, Int>>(
        decodeSelectedColors(preferenceManager.selectedIconColors.get()),
    )
    val selectedColors: StateFlow<Map<String, Int>> = _selectedColors.asStateFlow()

    /**
     * Uses [color] as the foreground colour of the icon of the app with the given
     * [packageName].
     */
    fun selectColor(packageName: String, color: Int) {
        val colors = _selectedColors.updateAndGet { it + (packageName to color) }
        preferenceManager.selectedIconColors.set(encodeSelectedColors(colors))
    }

    /**
     * Forgets the colour picked for the app with the given [packageName], so that the
     * app uses the global foreground colour again.
     */
    fun clearColor(packageName: String) {
        val colors = _selectedColors.updateAndGet { it - packageName }
        preferenceManager.selectedIconColors.set(encodeSelectedColors(colors))
    }

    /**
     * Uses the icon with the given [drawableName] for the app with the given [packageName].
     */
    fun selectIcon(packageName: String, drawableName: String) {
        val icons = _selectedIconNames.updateAndGet { it + (packageName to drawableName) }
        preferenceManager.selectedIconNames.set(encodeSelectedIcons(icons))
    }

    /**
     * Forgets the icon picked for the app with the given [packageName], so that the app uses
     * the icon the pack maps to it again.
     */
    fun clearIcon(packageName: String) {
        val icons = _selectedIconNames.updateAndGet { it - packageName }
        preferenceManager.selectedIconNames.set(encodeSelectedIcons(icons))
    }

    /**
     * The settings which decide how the painted icons look. They are restored from the
     * preferences, so that they are remembered across restarts of the app.
     */
    private val _paintedIconSettings = MutableStateFlow(
        PaintedIconSettings(
            useOriginalForeground = preferenceManager.paintedUseOriginalForeground.get(),
            foregroundColor = preferenceManager.paintedForegroundColor.get(),
            backgroundColor = preferenceManager.paintedBackgroundColor.get(),
            shape = IconShape.fromName(preferenceManager.selectedIconShape.get()),
        ),
    )
    val paintedIconSettings: StateFlow<PaintedIconSettings> = _paintedIconSettings.asStateFlow()

    /**
     * Applies the given [settings] and remembers them for the next start of the app.
     */
    fun updatePaintedIconSettings(settings: PaintedIconSettings) {
        val previous = _paintedIconSettings.value
        if (previous == settings) return
        _paintedIconSettings.value = settings

        if (settings.useOriginalForeground != previous.useOriginalForeground) {
            preferenceManager.paintedUseOriginalForeground.set(settings.useOriginalForeground)
        }
        if (settings.foregroundColor != previous.foregroundColor) {
            preferenceManager.paintedForegroundColor.set(settings.foregroundColor)
        }
        if (settings.backgroundColor != previous.backgroundColor) {
            preferenceManager.paintedBackgroundColor.set(settings.backgroundColor)
        }
        if (settings.shape != previous.shape) {
            preferenceManager.selectedIconShape.set(settings.shape.name)
        }
    }

    /**
     * The state of the export of the painted icons as an icon pack.
     */
    private val _exportState = MutableStateFlow<IconPackExportState>(IconPackExportState.Idle)
    val exportState: StateFlow<IconPackExportState> = _exportState.asStateFlow()

    /**
     * Builds an icon pack with the painted icons of the covered apps and saves it
     * as a plain file.
     */
    fun exportIconPack() {
        if (_exportState.value is IconPackExportState.Building) return
        _exportState.value = IconPackExportState.Building
        viewModelScope.launch {
            _exportState.value = try {
                val location = iconPackExportRepository.export(
                    installedApps.value,
                    selectedColors.value,
                    _paintedIconSettings.value,
                )
                IconPackExportState.Saved(location)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e("AppsViewModel", "Could not export the icon pack", e)
                IconPackExportState.Failed(e.message ?: e.toString())
            }
        }
    }

    /**
     * Returns the export to [IconPackExportState.Idle], once it is done.
     */
    fun dismissExportResult() {
        if (_exportState.value !is IconPackExportState.Building) {
            _exportState.value = IconPackExportState.Idle
        }
    }
}
