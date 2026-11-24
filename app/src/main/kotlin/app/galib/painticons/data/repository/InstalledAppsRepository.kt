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

package app.galib.painticons.data.repository

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import app.galib.painticons.data.model.InstalledApp
import app.galib.painticons.data.util.dominantColors
import app.galib.painticons.data.util.toIconBitmap
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface InstalledAppsRepository {
    val installedApps: StateFlow<List<InstalledApp>>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InstalledAppsRepositoryImpl(
    private val application: Application,
) : InstalledAppsRepository {

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps = _installedApps.asStateFlow()

    init {
        coroutineScope.launch {
            _installedApps.value = loadInstalledApps()
        }
    }

    private fun loadInstalledApps(): List<InstalledApp> {
        val packageManager = application.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val iconSize = (ICON_SIZE_DP * application.resources.displayMetrics.density).toInt()

        return packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { loadInstalledApp(it, packageManager, iconSize) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private fun loadInstalledApp(
        resolveInfo: ResolveInfo,
        packageManager: PackageManager,
        iconSize: Int,
    ): InstalledApp? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val appInfo = activityInfo.applicationInfo ?: return null
        val icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull()
            ?: return null

        return InstalledApp(
            label = resolveInfo.loadLabel(packageManager).toString(),
            packageName = appInfo.packageName,
            className = activityInfo.name,
            colors = icon.toIconBitmap(iconSize).dominantColors(),
        )
    }

    private companion object {
        /** The size of the icons in the grid, in dp. */
        const val ICON_SIZE_DP = 48
    }
}
