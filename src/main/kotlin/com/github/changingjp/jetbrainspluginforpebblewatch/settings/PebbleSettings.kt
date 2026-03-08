package com.github.changingjp.jetbrainspluginforpebblewatch.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic

private val DEFAULT_PEBBLE_PATH: String by lazy {
    try {
        val process = ProcessBuilder("which", "pebble").start()
        val result = process.inputStream.bufferedReader().readLine()?.trim()
        if (!result.isNullOrEmpty() && process.waitFor() == 0) result else "pebble"
    } catch (_: Exception) {
        "pebble"
    }
}

@State(
    name = "PebbleSettings",
    storages = [Storage("pebble-settings.xml")]
)
@Service(Service.Level.APP)
class PebbleSettings : PersistentStateComponent<PebbleSettings.State> {

    data class State(var pebblePath: String = DEFAULT_PEBBLE_PATH)

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun interface Listener {
        fun settingsChanged()
    }

    companion object {
        val TOPIC: Topic<Listener> = Topic.create("PebbleSettingsChanged", Listener::class.java)

        fun getInstance(): PebbleSettings =
            ApplicationManager.getApplication().getService(PebbleSettings::class.java)
    }
}