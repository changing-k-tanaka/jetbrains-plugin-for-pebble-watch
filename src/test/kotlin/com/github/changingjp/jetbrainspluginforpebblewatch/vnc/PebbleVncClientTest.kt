package com.github.changingjp.jetbrainspluginforpebblewatch.vnc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.image.BufferedImage

class PebbleVncClientTest {

    @Test
    fun testRunningIsFalseInitially() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        assertFalse(client.running)
    }

    @Test
    fun testVncWidthIsZeroInitially() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        assertEquals(0, client.vncWidth)
    }

    @Test
    fun testVncHeightIsZeroInitially() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        assertEquals(0, client.vncHeight)
    }

    @Test
    fun testDisconnectBeforeConnectDoesNotThrow() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        client.disconnect()
    }

    @Test
    fun testRunningIsFalseAfterDisconnect() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        client.disconnect()
        assertFalse(client.running)
    }

    @Test
    fun testDisconnectCalledTwiceDoesNotThrow() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        client.disconnect()
        client.disconnect()
    }

    @Test
    fun testClientCreationWithDifferentHostAndPort() {
        val client = PebbleVncClient("192.168.1.100", 5901) { _: BufferedImage -> }
        assertNotNull(client)
        assertFalse(client.running)
    }

    @Test
    fun testSendPointerEventBeforeConnectDoesNotThrow() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        // outp is null, so write { } should be a no-op
        client.sendPointerEvent(0, 0, 0)
    }

    @Test
    fun testSendKeyEventBeforeConnectDoesNotThrow() {
        val client = PebbleVncClient("localhost", 5900) { _: BufferedImage -> }
        // outp is null, so write { } should be a no-op
        client.sendKeyEvent(0xFF52, true)
        client.sendKeyEvent(0xFF52, false)
    }
}
