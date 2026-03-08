package com.github.pebbleloveru.jetbrainspluginforpebblewatch.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.SwingUtilities

class PebbleSettingsConfigurable : Configurable {

    private data class SdkEntry(val version: String, val isActive: Boolean, val installed: Boolean) {
        override fun toString(): String = when {
            isActive -> "$version (active)"
            installed -> "$version (installed)"
            else -> "$version (not installed)"
        }
    }

    private var pathField: TextFieldWithBrowseButton? = null
    private var sdkComboBox: JComboBox<SdkEntry>? = null

    override fun getDisplayName(): String = "Pebble Development Plugin"

    override fun createComponent(): JComponent {
        val field = TextFieldWithBrowseButton().apply {
            text = PebbleSettings.getInstance().state.pebblePath
            addBrowseFolderListener(
                "Select Pebble Tool",
                "Select the pebble executable",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        }
        pathField = field

        val comboBox = JComboBox<SdkEntry>().apply { isEnabled = false }
        sdkComboBox = comboBox

        val applyButton = JButton("Apply").apply { isEnabled = false }
        val reloadButton = JButton("Reload")

        applyButton.addActionListener { applySdk() }
        reloadButton.addActionListener {
            reloadButton.isEnabled = false
            comboBox.isEnabled = false
            applyButton.isEnabled = false
            loadSdkList {
                reloadButton.isEnabled = true
                comboBox.isEnabled = comboBox.itemCount > 0
                applyButton.isEnabled = comboBox.itemCount > 0
            }
        }

        // パネル表示時に自動でリスト読み込み
        loadSdkList {
            comboBox.isEnabled = comboBox.itemCount > 0
            applyButton.isEnabled = comboBox.itemCount > 0
        }

        return JBPanel<JBPanel<*>>(GridBagLayout()).apply {
            // Row 0: Pebble Tool Path（col 1-3 を横断）
            add(JBLabel("Pebble Tool Path:"), GridBagConstraints().apply {
                gridx = 0; gridy = 0
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 4, 4, 8)
            })
            add(field, GridBagConstraints().apply {
                gridx = 1; gridy = 0
                gridwidth = 3
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                insets = Insets(4, 0, 4, 4)
            })

            // Row 1: SDK Version
            add(JBLabel("SDK Version:"), GridBagConstraints().apply {
                gridx = 0; gridy = 1
                anchor = GridBagConstraints.WEST
                insets = Insets(4, 4, 4, 8)
            })
            add(comboBox, GridBagConstraints().apply {
                gridx = 1; gridy = 1
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                insets = Insets(4, 0, 4, 4)
            })
            add(applyButton, GridBagConstraints().apply {
                gridx = 2; gridy = 1
                weightx = 0.0
                insets = Insets(4, 0, 4, 4)
            })
            add(reloadButton, GridBagConstraints().apply {
                gridx = 3; gridy = 1
                weightx = 0.0
                insets = Insets(4, 0, 4, 4)
            })

            // 下部余白フィラー
            add(JBPanel<JBPanel<*>>(), GridBagConstraints().apply {
                gridx = 0; gridy = 2
                weighty = 1.0
                fill = GridBagConstraints.VERTICAL
            })
        }
    }

    private fun loadSdkList(onLoaded: () -> Unit = {}) {
        val pebble = pathField?.text?.trim().orEmpty().ifBlank { "pebble" }
        ApplicationManager.getApplication().executeOnPooledThread {
            val entries = mutableListOf<SdkEntry>()
            runCatching {
                val process = ProcessBuilder("bash", "-c", "$pebble sdk list")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                var section: String? = null
                for (line in output.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
                    when {
                        line.startsWith("Installed SDKs") -> section = "installed"
                        line.startsWith("Available SDKs") -> section = "available"
                        line.startsWith("Could not fetch") -> { /* skip */ }
                        section != null -> {
                            val isActive = line.contains("(active)")
                            val version = line.replace("(active)", "").trim()
                            entries.add(SdkEntry(version, isActive, section == "installed"))
                        }
                    }
                }
            }

            SwingUtilities.invokeLater {
                val combo = sdkComboBox ?: return@invokeLater
                combo.removeAllItems()
                entries.forEach { combo.addItem(it) }
                // active なバージョンをデフォルト選択
                val activeIndex = entries.indexOfFirst { it.isActive }
                if (activeIndex >= 0) combo.selectedIndex = activeIndex
                onLoaded()
            }
        }
    }

    private fun applySdk() {
        val entry = sdkComboBox?.selectedItem as? SdkEntry ?: return

        if (entry.isActive) {
            Messages.showInfoMessage("SDK ${entry.version} is already active.", "SDK")
            return
        }

        val pebble = pathField?.text?.trim().orEmpty().ifBlank { "pebble" }
        val subCommand = if (entry.installed) "activate" else "install"
        val taskTitle = if (entry.installed) "Activating SDK ${entry.version}..." else "Installing SDK ${entry.version}..."
        sdkComboBox?.isEnabled = false

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, taskTitle, false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    ProcessBuilder("bash", "-c", "$pebble sdk $subCommand ${entry.version}")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()

                    // TODO: which pebble でパスを自動更新（環境によって取得できないため一旦コメントアウト）
                    // val whichProcess = ProcessBuilder("bash", "-c", "which pebble")
                    //     .redirectErrorStream(true)
                    //     .start()
                    // val whichOutput = whichProcess.inputStream.bufferedReader().readText().trim()
                    // whichProcess.waitFor()
                    // if (whichOutput.isNotEmpty()) {
                    //     SwingUtilities.invokeLater { pathField?.text = whichOutput }
                    // } else {
                    //     SwingUtilities.invokeLater {
                    //         Messages.showErrorDialog(
                    //             "'which pebble' でパスを取得できませんでした。\nSDK が正常にインストールされなかった可能性があります。",
                    //             "SDK Error"
                    //         )
                    //     }
                    // }

                    SwingUtilities.invokeLater {
                        Messages.showInfoMessage("SDK ${entry.version} applied.", "SDK Applied")
                        loadSdkList { sdkComboBox?.isEnabled = true }
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog("Failed: ${e.message}", "SDK Error")
                        sdkComboBox?.isEnabled = true
                    }
                }
            }
        })
    }

    override fun isModified(): Boolean =
        pathField?.text?.trim() != PebbleSettings.getInstance().state.pebblePath

    override fun apply() {
        PebbleSettings.getInstance().state.pebblePath = pathField?.text?.trim().orEmpty().ifBlank { "pebble" }
        ApplicationManager.getApplication().messageBus
            .syncPublisher(PebbleSettings.TOPIC)
            .settingsChanged()
    }

    override fun reset() {
        pathField?.text = PebbleSettings.getInstance().state.pebblePath
    }

    override fun disposeUIResources() {
        // 設定ダイアログを閉じたらツールウィンドウの Reload を自動トリガー
        ApplicationManager.getApplication().messageBus
            .syncPublisher(PebbleSettings.TOPIC)
            .settingsChanged()
        pathField = null
        sdkComboBox = null
    }
}