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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceManagerTest {

    private class TestPreferenceManager(
        store: PreferenceStore,
    ) : BasePreferenceManager(store) {
        val boolPref = BoolPref("bool_key", false)
        val intPref = IntPref("int_key", 7)
        val stringPref = StringPref("string_key", "default")
    }

    @Test
    fun `bool pref returns default before writes`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        assertFalse(manager.boolPref.get())
    }

    @Test
    fun `bool pref set persists value`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.boolPref.set(true)

        assertTrue(manager.boolPref.get())
    }

    @Test
    fun `bool pref toggle flips current value`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.boolPref.toggle()
        assertTrue(manager.boolPref.get())

        manager.boolPref.toggle()
        assertFalse(manager.boolPref.get())
    }

    @Test
    fun `int pref returns default before writes`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        assertEquals(7, manager.intPref.get())
    }

    @Test
    fun `int pref set persists value`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.intPref.set(42)

        assertEquals(42, manager.intPref.get())
    }

    @Test
    fun `prefs keep values isolated by key`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.boolPref.set(true)
        manager.intPref.set(99)

        assertTrue(manager.boolPref.get())
        assertEquals(99, manager.intPref.get())
    }

    @Test
    fun `string pref returns default before writes`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        assertEquals("default", manager.stringPref.get())
    }

    @Test
    fun `string pref set persists value`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.stringPref.set("value")

        assertEquals("value", manager.stringPref.get())
    }

    @Test
    fun `string pref keeps the value of an empty string`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.stringPref.set("")

        assertEquals("", manager.stringPref.get())
    }

    @Test
    fun `prefs of different types keep values isolated by key`() {
        val manager = TestPreferenceManager(InMemoryPreferenceStore())

        manager.boolPref.set(true)
        manager.intPref.set(99)
        manager.stringPref.set("value")

        assertTrue(manager.boolPref.get())
        assertEquals(99, manager.intPref.get())
        assertEquals("value", manager.stringPref.get())
    }

    @Test
    fun `preference manager selected icon colors default to empty and can be set`() {
        val manager = PreferenceManager(InMemoryPreferenceStore())

        assertEquals("", manager.selectedIconColors.get())
        manager.selectedIconColors.set("""{"com.android.email":-16777216}""")

        assertEquals("""{"com.android.email":-16777216}""", manager.selectedIconColors.get())
    }

    @Test
    fun `preference manager selected icon names default to empty and can be set`() {
        val manager = PreferenceManager(InMemoryPreferenceStore())

        assertEquals("", manager.selectedIconNames.get())
        manager.selectedIconNames.set("""{"com.android.email":"email"}""")

        assertEquals("""{"com.android.email":"email"}""", manager.selectedIconNames.get())
    }

    @Test
    fun `preference manager selected icon shape defaults to no shape and can be set`() {
        val manager = PreferenceManager(InMemoryPreferenceStore())

        assertEquals("None", manager.selectedIconShape.get())
        manager.selectedIconShape.set("Heart")

        assertEquals("Heart", manager.selectedIconShape.get())
    }
}
