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

package app.galib.painticons.data.util

import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

/**
 * Renders the drawable into a square bitmap of [size] pixels.
 *
 * Adaptive icons are drawn with a transparent margin (the outer sixth on each side,
 * which the launcher would normally mask away), so they are rendered larger and
 * cropped to make the icon fill the whole item.
 */
fun Drawable.toIconBitmap(size: Int): Bitmap {
    if (this !is AdaptiveIconDrawable) return toBitmap(size, size)

    val renderSize = size * 3 / 2
    val bitmap = toBitmap(renderSize, renderSize)
    val inset = renderSize / 6
    val side = renderSize - inset * 2
    return runCatching {
        Bitmap.createBitmap(bitmap, inset, inset, side, side)
    }.getOrDefault(bitmap)
}
