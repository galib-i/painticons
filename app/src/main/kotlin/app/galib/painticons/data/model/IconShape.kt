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

package app.galib.painticons.data.model

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import app.galib.painticons.R

/**
 * The shapes the painted icons of the apps can have.
 *
 * These are the shapes Material 3 offers, so that the painted icons match the expressive
 * Material 3 components.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class IconShape(
    /** The label of the shape, shown to the user. */
    val labelRes: Int,
    /** The polygon of the shape, which is normalized to the unit square. */
    val polygon: RoundedPolygon?,
) {
    None(R.string.shape_none, null),
    Circle(R.string.shape_circle, MaterialShapes.Circle),
    Square(R.string.shape_square, MaterialShapes.Square),
    Slanted(R.string.shape_slanted, MaterialShapes.Slanted),
    Arch(R.string.shape_arch, MaterialShapes.Arch),
    Arrow(R.string.shape_arrow, MaterialShapes.Arrow),
    SemiCircle(R.string.shape_semi_circle, MaterialShapes.SemiCircle),
    Oval(R.string.shape_oval, MaterialShapes.Oval),
    Pill(R.string.shape_pill, MaterialShapes.Pill),
    Triangle(R.string.shape_triangle, MaterialShapes.Triangle),
    Diamond(R.string.shape_diamond, MaterialShapes.Diamond),
    ClamShell(R.string.shape_clam_shell, MaterialShapes.ClamShell),
    Pentagon(R.string.shape_pentagon, MaterialShapes.Pentagon),
    Gem(R.string.shape_gem, MaterialShapes.Gem),
    Heart(R.string.shape_heart, MaterialShapes.Heart),
    Sunny(R.string.shape_sunny, MaterialShapes.Sunny),
    VerySunny(R.string.shape_very_sunny, MaterialShapes.VerySunny),
    Cookie4Sided(R.string.shape_cookie_4_sided, MaterialShapes.Cookie4Sided),
    Cookie6Sided(R.string.shape_cookie_6_sided, MaterialShapes.Cookie6Sided),
    Cookie7Sided(R.string.shape_cookie_7_sided, MaterialShapes.Cookie7Sided),
    Cookie9Sided(R.string.shape_cookie_9_sided, MaterialShapes.Cookie9Sided),
    Cookie12Sided(R.string.shape_cookie_12_sided, MaterialShapes.Cookie12Sided),
    Ghostish(R.string.shape_ghostish, MaterialShapes.Ghostish),
    Clover4Leaf(R.string.shape_clover_4_leaf, MaterialShapes.Clover4Leaf),
    Clover8Leaf(R.string.shape_clover_8_leaf, MaterialShapes.Clover8Leaf),
    Fan(R.string.shape_fan, MaterialShapes.Fan),
    Burst(R.string.shape_burst, MaterialShapes.Burst),
    SoftBurst(R.string.shape_soft_burst, MaterialShapes.SoftBurst),
    Boom(R.string.shape_boom, MaterialShapes.Boom),
    SoftBoom(R.string.shape_soft_boom, MaterialShapes.SoftBoom),
    Flower(R.string.shape_flower, MaterialShapes.Flower),
    Puffy(R.string.shape_puffy, MaterialShapes.Puffy),
    PuffyDiamond(R.string.shape_puffy_diamond, MaterialShapes.PuffyDiamond),
    Bun(R.string.shape_bun, MaterialShapes.Bun),
    PixelCircle(R.string.shape_pixel_circle, MaterialShapes.PixelCircle),
    PixelTriangle(R.string.shape_pixel_triangle, MaterialShapes.PixelTriangle),
    ;

    companion object {
        /** The shape which is used when the user hasn't picked one (yet). */
        val Default = None

        /**
         * The shape with the given [name], or [Default] if there is no shape with that name.
         */
        fun fromName(name: String?): IconShape =
            entries.firstOrNull { it.name == name } ?: Default
    }
}
