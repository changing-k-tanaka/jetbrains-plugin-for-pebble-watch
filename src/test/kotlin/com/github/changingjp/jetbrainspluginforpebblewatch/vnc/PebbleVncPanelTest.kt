package com.github.changingjp.jetbrainspluginforpebblewatch.vnc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.Dimension

class PebbleVncPanelTest {

    // ── Companion object constants ──────────────────────────────────────────

    @Test
    fun testKeyBackConstant() {
        assertEquals(0xFF51, PebbleVncPanel.KEY_BACK)
    }

    @Test
    fun testKeyUpConstant() {
        assertEquals(0xFF52, PebbleVncPanel.KEY_UP)
    }

    @Test
    fun testKeySelectConstant() {
        assertEquals(0xFF53, PebbleVncPanel.KEY_SELECT)
    }

    @Test
    fun testKeyDownConstant() {
        assertEquals(0xFF54, PebbleVncPanel.KEY_DOWN)
    }

    @Test
    fun testKeyConstantsAreDistinct() {
        val keys = setOf(PebbleVncPanel.KEY_BACK, PebbleVncPanel.KEY_UP, PebbleVncPanel.KEY_SELECT, PebbleVncPanel.KEY_DOWN)
        assertEquals(4, keys.size)
    }

    // ── Panel instantiation ─────────────────────────────────────────────────

    @Test
    fun testDefaultConstructionDoesNotThrow() {
        val panel = PebbleVncPanel()
        assertNotNull(panel)
    }

    @Test
    fun testCustomHostPortConstructionDoesNotThrow() {
        val panel = PebbleVncPanel(host = "10.0.0.1", port = 5902)
        assertNotNull(panel)
    }

    // ── preferredSize before any VNC frame ──────────────────────────────────

    @Test
    fun testDefaultPreferredSizeWidth() {
        val panel = PebbleVncPanel()
        assertEquals(288, panel.preferredSize.width)
    }

    @Test
    fun testDefaultPreferredSizeHeight() {
        val panel = PebbleVncPanel()
        assertEquals(336, panel.preferredSize.height)
    }

    @Test
    fun testDefaultPreferredSizeDimension() {
        val panel = PebbleVncPanel()
        assertEquals(Dimension(288, 336), panel.preferredSize)
    }

    // ── dispose ─────────────────────────────────────────────────────────────

    @Test
    fun testDisposeDoesNotThrow() {
        val panel = PebbleVncPanel()
        panel.dispose()
    }

    @Test
    fun testDisposeMultipleTimesDoesNotThrow() {
        val panel = PebbleVncPanel()
        panel.dispose()
        panel.dispose()
    }

    // ── pressButton with no active client ───────────────────────────────────

    @Test
    fun testPressButtonBackWithNoClient() {
        val panel = PebbleVncPanel()
        panel.pressButton(PebbleVncPanel.KEY_BACK)
    }

    @Test
    fun testPressButtonUpWithNoClient() {
        val panel = PebbleVncPanel()
        panel.pressButton(PebbleVncPanel.KEY_UP)
    }

    @Test
    fun testPressButtonSelectWithNoClient() {
        val panel = PebbleVncPanel()
        panel.pressButton(PebbleVncPanel.KEY_SELECT)
    }

    @Test
    fun testPressButtonDownWithNoClient() {
        val panel = PebbleVncPanel()
        panel.pressButton(PebbleVncPanel.KEY_DOWN)
    }

    @Test
    fun testPressButtonAfterDisposeDoesNotThrow() {
        val panel = PebbleVncPanel()
        panel.dispose()
        panel.pressButton(PebbleVncPanel.KEY_UP)
    }
}
