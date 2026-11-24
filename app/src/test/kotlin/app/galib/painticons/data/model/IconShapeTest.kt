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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconShapeTest {

    @Test
    fun `default shape is no shape`() {
        assertEquals(IconShape.None, IconShape.Default)
    }

    @Test
    fun `shape is found by its name`() {
        assertEquals(IconShape.PuffyDiamond, IconShape.fromName("PuffyDiamond"))
        assertEquals(IconShape.Cookie12Sided, IconShape.fromName("Cookie12Sided"))
    }

    @Test
    fun `unknown shape name falls back to the default shape`() {
        assertEquals(IconShape.Default, IconShape.fromName("Nonexistent"))
        assertEquals(IconShape.Default, IconShape.fromName(""))
        assertEquals(IconShape.Default, IconShape.fromName(null))
    }

    @Test
    fun `every shape has a unique name`() {
        val names = IconShape.entries.map { it.name }

        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `every shape is normalized to the unit square`() {
        IconShape.entries.forEach { shape ->
            val polygon = shape.polygon ?: return@forEach
            val bounds = polygon.calculateBounds()

            assertTrue("${shape.name} starts at ${bounds[0]}", bounds[0] >= -Tolerance)
            assertTrue("${shape.name} starts at ${bounds[1]}", bounds[1] >= -Tolerance)
            assertTrue("${shape.name} ends at ${bounds[2]}", bounds[2] <= 1 + Tolerance)
            assertTrue("${shape.name} ends at ${bounds[3]}", bounds[3] <= 1 + Tolerance)
        }
    }

    private companion object {
        /** The tolerance of the normalization check, to account for floating point errors. */
        const val Tolerance = 0.001f
    }
}
