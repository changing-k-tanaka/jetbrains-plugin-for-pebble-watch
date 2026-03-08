package com.github.pebbleloveru.jetbrainspluginforpebblewatch.vnc

import com.intellij.openapi.application.ApplicationManager
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Swing panel that shows a live VNC view of the Pebble emulator.
 * Connects to localhost:5901 (QEMU VNC display :1) and retries
 * automatically until the emulator is ready.
 * Forwards mouse clicks and keyboard events back to the emulator.
 */
class PebbleVncPanel(
    private val host: String = "localhost",
    private val port: Int = 5901
) : JPanel() {

    companion object {
        /** X11 key symbols for Pebble watch hardware buttons (QEMU key bindings) */
        const val KEY_BACK   = 0xFF51  // XK_Left   → Back button
        const val KEY_UP     = 0xFF52  // XK_Up     → Up button
        const val KEY_SELECT = 0xFF53  // XK_Right  → Select/Action button
        const val KEY_DOWN   = 0xFF54  // XK_Down   → Down button
    }

    @Volatile private var currentImage: BufferedImage? = null
    @Volatile private var client: PebbleVncClient? = null
    @Volatile private var disposed = false
    @Volatile private var statusText = "Connecting to emulator..."

    /** Called on the EDT when the first VNC frame is received. */
    var onFirstFrame: (() -> Unit)? = null

    private var buttonMask = 0

    init {
        background = Color.BLACK
        isFocusable = true
        cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                buttonMask = buttonMask or e.awtButtonMask()
                sendPointer(e)
            }
            override fun mouseReleased(e: MouseEvent) {
                buttonMask = buttonMask and e.awtButtonMask().inv()
                sendPointer(e)
            }
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) = sendPointer(e)
            override fun mouseDragged(e: MouseEvent) = sendPointer(e)
        })

        addMouseWheelListener { e ->
            // Scroll up: button 4, scroll down: button 5
            val mask = if (e.wheelRotation < 0) 0x08 else 0x10
            val c = client ?: return@addMouseWheelListener
            val vx = toVncX(e.x, c); val vy = toVncY(e.y, c)
            c.sendPointerEvent(mask, vx, vy)
            c.sendPointerEvent(0, vx, vy)
        }

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                client?.sendKeyEvent(toKeySym(e), true)
            }
            override fun keyReleased(e: KeyEvent) {
                client?.sendKeyEvent(toKeySym(e), false)
            }
        })
    }

    // Returns the VNC image's natural size, or a placeholder size before the first frame.
    override fun getPreferredSize(): Dimension =
        currentImage?.let { Dimension(it.width, it.height) } ?: Dimension(288, 336)

    /** Call this to start connecting. Retries every second until emulator is ready. */
    fun startConnection() {
        ApplicationManager.getApplication().executeOnPooledThread {
            while (!disposed) {
                runCatching {
                    val c = PebbleVncClient(host, port) { img ->
                        val isFirst = currentImage == null
                        currentImage = img
                        SwingUtilities.invokeLater {
                            if (isFirst) {
                                revalidate()
                                onFirstFrame?.invoke()
                            }
                            repaint()
                        }
                    }
                    client = c
                    SwingUtilities.invokeLater { statusText = "Connected"; repaint() }
                    c.connect() // blocks until disconnected
                }
                if (!disposed) {
                    client = null
                    SwingUtilities.invokeLater { statusText = "Connecting to emulator..."; repaint() }
                    Thread.sleep(1000)
                }
            }
        }
    }

    /**
     * Simulate a Pebble hardware button press via VNC key event.
     * Sends key-down immediately and key-up after 100 ms on a pooled thread.
     */
    fun pressButton(keySym: Int) {
        val c = client ?: return
        c.sendKeyEvent(keySym, true)
        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(100)
            c.sendKeyEvent(keySym, false)
        }
    }

    fun dispose() {
        disposed = true
        client?.disconnect()
    }

    // ── Painting ───────────────────────────────────────────────────────────

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val img = currentImage
        if (img != null) {
            g.drawImage(img, 0, 0, null)  // 1:1 — no scaling
        } else {
            g.color = Color(30, 30, 30)
            g.fillRect(0, 0, width, height)
            g.color = Color.GRAY
            g.font = g.font.deriveFont(12f)
            g.drawString(statusText, 10, height / 2)
        }
    }

    // ── Input forwarding ───────────────────────────────────────────────────

    private fun sendPointer(e: MouseEvent) {
        val c = client ?: return
        c.sendPointerEvent(buttonMask, toVncX(e.x, c), toVncY(e.y, c))
    }

    private fun toVncX(px: Int, c: PebbleVncClient) =
        (px.toDouble() / width * c.vncWidth).toInt().coerceIn(0, maxOf(c.vncWidth - 1, 0))

    private fun toVncY(py: Int, c: PebbleVncClient) =
        (py.toDouble() / height * c.vncHeight).toInt().coerceIn(0, maxOf(c.vncHeight - 1, 0))

    private fun MouseEvent.awtButtonMask(): Int = when (button) {
        MouseEvent.BUTTON1 -> 0x01
        MouseEvent.BUTTON2 -> 0x02
        MouseEvent.BUTTON3 -> 0x04
        else -> 0
    }

    /** Map Java KeyEvent key codes to X11 key symbols used by the RFB protocol. */
    private fun toKeySym(e: KeyEvent): Int = when (e.keyCode) {
        KeyEvent.VK_UP         -> 0xFF52
        KeyEvent.VK_DOWN       -> 0xFF54
        KeyEvent.VK_LEFT       -> 0xFF51
        KeyEvent.VK_RIGHT      -> 0xFF53
        KeyEvent.VK_ENTER      -> 0xFF0D
        KeyEvent.VK_ESCAPE     -> 0xFF1B
        KeyEvent.VK_BACK_SPACE -> 0xFF08
        KeyEvent.VK_DELETE     -> 0xFFFF
        KeyEvent.VK_TAB        -> 0xFF09
        KeyEvent.VK_F1         -> 0xFFBE
        KeyEvent.VK_F2         -> 0xFFBF
        KeyEvent.VK_F3         -> 0xFFC0
        KeyEvent.VK_F4         -> 0xFFC1
        else -> {
            val ch = e.keyChar
            if (ch != KeyEvent.CHAR_UNDEFINED && ch.code in 0x20..0x7E) ch.code
            else e.keyCode
        }
    }
}