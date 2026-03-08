package com.github.changingjp.jetbrainspluginforpebblewatch.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PebbleSettingsTest : BasePlatformTestCase() {

    fun testDefaultStateIsNotNull() {
        val settings = PebbleSettings()
        assertNotNull(settings.state)
    }

    fun testDefaultPebblePathIsNotEmpty() {
        val settings = PebbleSettings()
        assertTrue(settings.state.pebblePath.isNotEmpty())
    }

    fun testLoadStateUpdatesPebblePath() {
        val settings = PebbleSettings()
        val newState = PebbleSettings.State(pebblePath = "/custom/pebble")
        settings.loadState(newState)
        assertEquals("/custom/pebble", settings.state.pebblePath)
    }

    fun testGetStateAfterLoadStateReturnsSameObject() {
        val settings = PebbleSettings()
        val newState = PebbleSettings.State(pebblePath = "/usr/local/bin/pebble")
        settings.loadState(newState)
        assertSame(newState, settings.state)
    }

    fun testStateDataClassEquality() {
        val state1 = PebbleSettings.State(pebblePath = "pebble")
        val state2 = PebbleSettings.State(pebblePath = "pebble")
        assertEquals(state1, state2)
    }

    fun testStateDataClassInequality() {
        val state1 = PebbleSettings.State(pebblePath = "pebble")
        val state2 = PebbleSettings.State(pebblePath = "/other/pebble")
        assertFalse(state1 == state2)
    }

    fun testStateDataClassCopyPreservesOriginal() {
        val state = PebbleSettings.State(pebblePath = "/original/pebble")
        val copied = state.copy(pebblePath = "/copied/pebble")
        assertEquals("/copied/pebble", copied.pebblePath)
        assertEquals("/original/pebble", state.pebblePath)
    }

    fun testStateHashCodeConsistency() {
        val state1 = PebbleSettings.State(pebblePath = "pebble")
        val state2 = PebbleSettings.State(pebblePath = "pebble")
        assertEquals(state1.hashCode(), state2.hashCode())
    }

    fun testStateToStringContainsFieldName() {
        val state = PebbleSettings.State(pebblePath = "pebble")
        val str = state.toString()
        assertTrue(str.contains("pebblePath"))
    }

    fun testStateToStringContainsValue() {
        val state = PebbleSettings.State(pebblePath = "mypebble")
        assertTrue(state.toString().contains("mypebble"))
    }

    fun testTopicIsNotNull() {
        assertNotNull(PebbleSettings.TOPIC)
    }

    fun testGetInstanceIsNotNull() {
        val instance = PebbleSettings.getInstance()
        assertNotNull(instance)
    }

    fun testGetInstanceReturnsSingleton() {
        val instance1 = PebbleSettings.getInstance()
        val instance2 = PebbleSettings.getInstance()
        assertSame(instance1, instance2)
    }

    fun testGetStateReturnsCurrentState() {
        val settings = PebbleSettings()
        val state = settings.state
        assertNotNull(state)
        assertEquals(state, settings.state)
    }

    fun testMultipleLoadStates() {
        val settings = PebbleSettings()
        settings.loadState(PebbleSettings.State(pebblePath = "/first"))
        settings.loadState(PebbleSettings.State(pebblePath = "/second"))
        assertEquals("/second", settings.state.pebblePath)
    }
}
