package com.github.pebbleloveru.jetbrainspluginforpebblewatch.vnc

import java.awt.image.BufferedImage
import java.io.*
import java.net.Socket

/**
 * Minimal RFB (VNC) protocol client for the Pebble emulator.
 * Connects to QEMU's VNC server (default: localhost:5901).
 * Requests 32bpp RGB little-endian, Raw encoding only.
 */
class PebbleVncClient(
    private val host: String,
    private val port: Int,
    private val onUpdate: (BufferedImage) -> Unit
) {
    @Volatile var running = false
        private set

    private var socket: Socket? = null
    private var inp: DataInputStream? = null
    private var outp: DataOutputStream? = null
    private val writeLock = Any()

    var vncWidth = 0
        private set
    var vncHeight = 0
        private set

    private var image: BufferedImage? = null

    fun connect() {
        val sock = Socket(host, port)
        socket = sock
        inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
        outp = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
        handshake()
        running = true
        receiveLoop()
    }

    fun disconnect() {
        running = false
        runCatching { socket?.close() }
    }

    // ── Handshake ──────────────────────────────────────────────────────────

    private fun handshake() {
        val i = inp!!

        // Version negotiation
        val verBuf = ByteArray(12)
        i.readFully(verBuf)
        write { write("RFB 003.008\n".toByteArray(Charsets.US_ASCII)) }

        // Security type selection (RFB 3.7+)
        val numTypes = i.read()
        if (numTypes == 0) {
            val len = i.readInt()
            val msg = ByteArray(len)
            i.readFully(msg)
            throw IOException("Server refused connection: ${String(msg)}")
        }
        val types = ByteArray(numTypes)
        i.readFully(types)
        // Prefer type 1 (None); fall back to first offered type
        write { write(if (1.toByte() in types) 1 else types[0].toInt()) }

        // Security result
        val result = i.readInt()
        if (result != 0) {
            val len = i.readInt()
            val msg = ByteArray(len); i.readFully(msg)
            throw IOException("Auth failed: ${String(msg)}")
        }

        // ClientInit: shared session
        write { write(1) }

        // ServerInit
        vncWidth = i.readUnsignedShort()
        vncHeight = i.readUnsignedShort()
        i.readFully(ByteArray(16)) // pixel format – overridden below
        val nameLen = i.readInt()
        i.readFully(ByteArray(nameLen))

        image = BufferedImage(vncWidth, vncHeight, BufferedImage.TYPE_INT_RGB)

        sendSetPixelFormat()
        sendSetEncodings(intArrayOf(0)) // Raw only
        sendUpdateRequest(incremental = false)
    }

    // ── Client → Server messages ───────────────────────────────────────────

    private fun sendSetPixelFormat() = write {
        // 32 bpp, RGB 8-8-8, little-endian, true-colour
        write(0);  write(0); write(0); write(0) // type + padding
        write(32); write(24)                     // bpp, depth
        write(0)                                 // big-endian-flag = false
        write(1)                                 // true-colour-flag
        writeShort(255); writeShort(255); writeShort(255) // R/G/B max
        write(16); write(8); write(0)            // R/G/B shift
        write(0);  write(0); write(0)            // padding
    }

    private fun sendSetEncodings(encodings: IntArray) = write {
        write(2); write(0); writeShort(encodings.size)
        encodings.forEach { writeInt(it) }
    }

    private fun sendUpdateRequest(incremental: Boolean) = write {
        write(3); write(if (incremental) 1 else 0)
        writeShort(0); writeShort(0)
        writeShort(vncWidth); writeShort(vncHeight)
    }

    fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) = write {
        write(5); write(buttonMask)
        writeShort(x); writeShort(y)
    }

    fun sendKeyEvent(keySym: Int, down: Boolean) = write {
        write(4); write(if (down) 1 else 0)
        writeShort(0); writeInt(keySym)
    }

    // ── Receive loop ───────────────────────────────────────────────────────

    private fun receiveLoop() {
        val i = inp!!
        while (running) {
            when (i.read()) {
                0    -> handleFramebufferUpdate()
                2    -> { /* bell – ignore */ }
                3    -> { // ServerCutText
                    i.read(); i.read(); i.read() // padding
                    i.readFully(ByteArray(i.readInt()))
                }
                -1   -> running = false
            }
        }
    }

    private fun handleFramebufferUpdate() {
        val i = inp!!
        i.read() // padding
        val numRects = i.readUnsignedShort()
        val img = image ?: return

        repeat(numRects) {
            val x = i.readUnsignedShort()
            val y = i.readUnsignedShort()
            val w = i.readUnsignedShort()
            val h = i.readUnsignedShort()
            val encoding = i.readInt()

            if (encoding == 0 && w > 0 && h > 0) {
                val data = ByteArray(w * h * 4) // 32 bpp
                i.readFully(data)
                var di = 0
                for (py in 0 until h) {
                    for (px in 0 until w) {
                        // Little-endian: byte0=B, byte1=G, byte2=R, byte3=unused
                        val b = data[di].toInt() and 0xFF
                        val g = data[di + 1].toInt() and 0xFF
                        val r = data[di + 2].toInt() and 0xFF
                        img.setRGB(x + px, y + py, (r shl 16) or (g shl 8) or b)
                        di += 4
                    }
                }
            }
        }

        onUpdate(img)
        sendUpdateRequest(incremental = true)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private inline fun write(block: DataOutputStream.() -> Unit) {
        val o = outp ?: return
        try {
            synchronized(writeLock) { o.block(); o.flush() }
        } catch (_: Exception) {}
    }
}