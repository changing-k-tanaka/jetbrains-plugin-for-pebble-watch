package com.github.changingjp.jetbrainspluginforpebblewatch.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import java.io.File

private val DEFAULT_PEBBLE_PATH: String by lazy {
    // シェル経由で which を試行（bash → zsh の順）
    val shellResult = listOf("bash", "zsh").firstNotNullOfOrNull { shell ->
        try {
            val process = ProcessBuilder(shell, "-c", "which pebble").start()
            val result = process.inputStream.bufferedReader().readLine()?.trim()
            if (!result.isNullOrEmpty() && process.waitFor() == 0) result else null
        } catch (_: Exception) {
            null
        }
    }
    if (shellResult != null) return@lazy shellResult

    // シェルで見つからない場合、既知のパスを順番に確認
    val home = System.getProperty("user.home")
    val candidates = listOf(
        "$home/.local/bin/pebble",
        "$home/.pebble-sdk/pebble",
        "$home/pebble-dev/pebble-sdk-4.5-linux64/bin/pebble",
        "/usr/local/bin/pebble",
        "/usr/bin/pebble",
    )
    candidates.firstOrNull { File(it).canExecute() } ?: "pebble"
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