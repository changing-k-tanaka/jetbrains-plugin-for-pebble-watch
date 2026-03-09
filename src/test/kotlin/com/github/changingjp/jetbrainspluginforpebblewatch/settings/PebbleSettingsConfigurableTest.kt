package com.github.changingjp.jetbrainspluginforpebblewatch.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PebbleSettingsConfigurableTest : BasePlatformTestCase() {

    fun testGetDisplayName() {
        val configurable = PebbleSettingsConfigurable()
        assertEquals("Pebble Development Plugin", configurable.displayName)
    }

    fun testIsModifiedBeforeCreateComponentDoesNotThrow() {
        val configurable = PebbleSettingsConfigurable()
        // pathField is null before createComponent(); this should not throw
        val result = configurable.isModified()
        // null?.text?.trim() != pebblePath evaluates to true when pathField is null
        assertTrue(result)
    }

    fun testDisposeUIResourcesWithoutCreateComponentDoesNotThrow() {
        val configurable = PebbleSettingsConfigurable()
        // Should not throw even without calling createComponent() first
        configurable.disposeUIResources()
    }

    fun testDisposeUIResourcesTwiceDoesNotThrow() {
        val configurable = PebbleSettingsConfigurable()
        configurable.disposeUIResources()
        configurable.disposeUIResources()
    }

    fun testResetWithoutCreateComponentDoesNotThrow() {
        val configurable = PebbleSettingsConfigurable()
        // pathField is null, so reset() is a no-op
        configurable.reset()
    }

    fun testApplyWithoutCreateComponentDoesNotThrow() {
        val configurable = PebbleSettingsConfigurable()
        // pathField is null; apply() uses orEmpty().ifBlank { "pebble" }
        configurable.apply()
        // Verify the setting was persisted with the fallback value
        val savedPath = PebbleSettings.getInstance().state.pebblePath
        assertNotNull(savedPath)
        assertTrue(savedPath.isNotEmpty())
    }
}
