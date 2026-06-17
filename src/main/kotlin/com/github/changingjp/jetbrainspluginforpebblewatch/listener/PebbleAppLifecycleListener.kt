package com.github.changingjp.jetbrainspluginforpebblewatch.listener

import com.intellij.ide.AppLifecycleListener
import java.util.concurrent.TimeUnit

class PebbleAppLifecycleListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
        try {
            ProcessBuilder("bash", "-c", "pkill -x qemu-pebble")
                .start()
                .waitFor(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }
}