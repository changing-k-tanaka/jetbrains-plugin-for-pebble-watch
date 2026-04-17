package com.github.changingjp.jetbrainspluginforpebblewatch.toolWindow

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.fileChooser.FileChooser
import java.nio.file.Path
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.github.changingjp.jetbrainspluginforpebblewatch.settings.PebbleSettings
import com.github.changingjp.jetbrainspluginforpebblewatch.settings.PebbleSettingsConfigurable
import com.github.changingjp.jetbrainspluginforpebblewatch.vnc.PebbleVncPanel
import java.awt.BorderLayout
import com.intellij.ui.JBColor
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBScrollPane
import javax.swing.Box
import javax.swing.JMenu
import javax.swing.JPopupMenu
import javax.swing.JSpinner
import javax.swing.JSplitPane
import javax.swing.JViewport
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities


class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(project)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    class MyToolWindow(private val project: Project) {

        data class ProjectTypeOption(val label: String, val flag: String)

        private val platforms = linkedMapOf(
            "Pebble Classic (aplite)"    to "aplite",
            "Pebble Time (basalt)"       to "basalt",
            "Pebble Time Round (chalk)"  to "chalk",
            "Pebble 2 (diorite)"         to "diorite",
            "Pebble 2 Duo (flint)"       to "flint",
            "Pebble Time 2 (emery)"      to "emery",
            "Pebble Round 2 (gabbro)"    to "gabbro",
        )
        private val platformCombo = ComboBox(platforms.keys.toTypedArray()).apply {
            selectedItem = "Pebble Time 2 (emery)"
        }
        private val withLogsCheckBox = JBCheckBox("with logs", false)

        private val phoneIpField = JBTextField().apply {
            emptyText.text = "Phone IP - e.g. 192.168.xxx.xxx"
        }
        private val withLogsPhoneCheckBox = JBCheckBox("with logs", false)

        private val pathLabel = JBLabel()
        private val versionLabel = JBLabel()

        fun getContent(): JComponent {
            val panel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)

                // Pebble Tool path & Settings Button
                add(createRow(pathLabel, JButton("Settings").apply {
                    addActionListener {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, PebbleSettingsConfigurable::class.java)
                    }
                }))

                // Pebble tool version & Reload Button
                add(createRow(versionLabel, JButton("Reload").apply {
                    addActionListener { reload() }
                }))

                // Empty Line margin
                add(Box.createVerticalStrut(16))

                add(TitledSeparator("Emulator Launch").apply {
                    alignmentX = 0.0f
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                })

                // Emulator Selector & Emulator Launch Button
                add(createRow(platformCombo, JButton("Emulator Launch").apply {
                    addActionListener { launchEmulator() }
                }))
                // with Logs checkbox
                withLogsCheckBox.alignmentX = 0.0f
                withLogsCheckBox.maximumSize = Dimension(Int.MAX_VALUE, withLogsCheckBox.preferredSize.height)
                add(withLogsCheckBox)

                // Screenshot button for emulator
                add(JButton("Screenshot").apply {
                    alignmentX = 0.0f
                    addActionListener { screenshotEmulator() }
                })

                // Empty Line margin
                add(Box.createVerticalStrut(16))

                add(TitledSeparator("Launch on Pebble Watch through Phone").apply {
                    alignmentX = 0.0f
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                })

                // Launch Watchface/App on User's Pebble through Companion App on Phone
                add(createRow(phoneIpField, JButton("Launch on Pebble").apply {
                    addActionListener { launchPhone() }
                }))
                // with Logs checkbox on Phone
                withLogsPhoneCheckBox.alignmentX = 0.0f
                withLogsPhoneCheckBox.maximumSize = Dimension(Int.MAX_VALUE, withLogsPhoneCheckBox.preferredSize.height)
                add(withLogsPhoneCheckBox)

                // Screenshot button for phone
                add(JButton("Screenshot").apply {
                    alignmentX = 0.0f
                    addActionListener { screenshotPhone() }
                })

                // Empty Line margin
                add(Box.createVerticalStrut(4))

                // Caution about launch on Phone
                listOf(
                    "※Required settings on the companion app side",
                    "・Devices → Select any device → Dev Connection",
                    "・Settings → Use LAN developer connection ON"
                ).forEach { text ->
                    val label = JBLabel(text)
                    label.alignmentX = 0.0f
                    label.maximumSize = Dimension(Int.MAX_VALUE, label.preferredSize.height)
                    add(label)
                }

                // Empty Line margin
                add(Box.createVerticalStrut(16))

                add(TitledSeparator("Wipe Emulator").apply {
                    alignmentX = 0.0f
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                })

                add(JButton("Wipe Emulator").apply {
                    alignmentX = 0.0f
                    addActionListener { wipeEmulator() }
                })

                // Empty Line margin
                add(Box.createVerticalStrut(16))

                add(TitledSeparator("Download").apply {
                    alignmentX = 0.0f
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                })

                add(JButton("Download PBW file").apply {
                    alignmentX = 0.0f
                    addActionListener { downloadPbw() }
                })

                add(JButton("Download Source Directory as Zip file").apply {
                    alignmentX = 0.0f
                    addActionListener { downloadZip() }
                })

                // Empty Line margin
                add(Box.createVerticalStrut(16))

                add(TitledSeparator("Create New Project").apply {
                    alignmentX = 0.0f
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                })

                add(JButton("Create New Project").apply {
                    alignmentX = 0.0f
                    addActionListener { createNewProject() }
                })
            }

            ApplicationManager.getApplication().messageBus
                .connect(project as Disposable)
                .subscribe(PebbleSettings.TOPIC, PebbleSettings.Listener { reload() })

            reload()
            return panel
        }

        private fun createRow(left: JComponent, right: JButton) =
            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                alignmentX = 0.0f
                maximumSize = Dimension(Int.MAX_VALUE, maxOf(left.preferredSize.height, right.preferredSize.height))
                add(left, BorderLayout.CENTER)
                add(right, BorderLayout.EAST)
            }

        private fun reload() {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            SwingUtilities.invokeLater {
                pathLabel.text = "　Pebble Tool: $pebblePath"
                versionLabel.text = "　Version: Loading..."
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                val versionText = try {
                    val process = ProcessBuilder(pebblePath, "--version")
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    if (process.waitFor() == 0) output else "SDK is not installed"
                } catch (_: Exception) {
                    "SDK is not installed"
                }
                SwingUtilities.invokeLater {
                    versionLabel.text = "　Version: $versionText"
                }
            }
        }

        /**
         * Gray button on the left side of the VNC panel — simulates the Back hardware button.
         * Uses GridLayout(3,1) so the button occupies the middle row (same height as one right-side button).
         */
        private fun createBackButton(vncPanel: PebbleVncPanel): JComponent =
            JBPanel<JBPanel<*>>(GridLayout(3, 1, 1, 4)).apply {
                preferredSize = Dimension(30, 0)
                isOpaque = false
                border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
                add(JBPanel<JBPanel<*>>().apply { isOpaque = false })   // top spacer
                add(JButton().apply {
                    background = JBColor.GRAY
                    isOpaque = true
                    isFocusable = false
                    addActionListener { vncPanel.pressButton(PebbleVncPanel.KEY_BACK) }
                })
                add(JBPanel<JBPanel<*>>().apply { isOpaque = false })   // bottom spacer
            }

        /**
         * Wraps vncPanel (with adjacent hardware buttons) in a scroll pane that keeps
         * the whole unit centered when the viewport is larger, and shows scroll bars when smaller.
         */
        private fun createVncScrollPane(vncPanel: PebbleVncPanel): JBScrollPane {
            val vncWithButtons = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                isOpaque = false
                add(createBackButton(vncPanel), BorderLayout.WEST)
                add(vncPanel, BorderLayout.CENTER)
                add(createRightSideButtons(vncPanel), BorderLayout.EAST)
            }
            val wrapper = object : JBPanel<JBPanel<*>>(GridBagLayout()) {
                override fun getPreferredSize(): Dimension {
                    val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport
                    val contentPref = vncWithButtons.preferredSize
                    return if (viewport != null) {
                        Dimension(maxOf(viewport.width, contentPref.width), maxOf(viewport.height, contentPref.height))
                    } else {
                        contentPref
                    }
                }
            }.apply {
                isOpaque = false
                add(vncWithButtons)
            }
            return JBScrollPane(wrapper).apply {
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy   = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            }
        }

        /** Three tall buttons on the right side of the VNC panel — Up / Select / Down. */
        private fun createRightSideButtons(vncPanel: PebbleVncPanel): JComponent =
            JBPanel<JBPanel<*>>(GridLayout(3, 1, 0, 4)).apply {
                preferredSize = Dimension(24, 0)
                listOf(PebbleVncPanel.KEY_UP, PebbleVncPanel.KEY_SELECT, PebbleVncPanel.KEY_DOWN)
                    .forEach { keySym ->
                        add(JButton().apply {
                            background = JBColor.GRAY
                            isOpaque = true
                            isFocusable = false
                            addActionListener { vncPanel.pressButton(keySym) }
                        })
                    }
            }

        private fun createEmulatorBottomBar(platform: String): JComponent =
            JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(6, 0, 0, 0)
                add(JButton("Open Config Browser").apply {
                    addActionListener { openConfigBrowser(platform) }
                }, BorderLayout.WEST)
                add(JButton("Emulate Settings").apply {
                    addActionListener { showEmulateSettingsMenu(this, platform) }
                }, BorderLayout.EAST)
            }

        private fun openConfigBrowser(platform: String) {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProcessBuilder("bash", "-c", "$pebblePath emu-app-config --emulator $platform --vnc")
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                } catch (_: Exception) {}
            }
        }

        private fun showEmulateSettingsMenu(source: JComponent, platform: String) {
            val menu = JPopupMenu()
            menu.add("Battery & Charge").addActionListener { showBatteryDialog(platform) }
            val btMenu = JMenu("Bluetooth State")
            btMenu.add("Active").addActionListener { setBluetooth(platform, true) }
            btMenu.add("Inactive").addActionListener { setBluetooth(platform, false) }
            menu.add(btMenu)
            menu.add("Emulate Tap").addActionListener { emulateTap(platform) }
            val timeMenu = JMenu("Time Format")
            timeMenu.add("12h").addActionListener { setTimeFormat(platform, "12h") }
            timeMenu.add("24h").addActionListener { setTimeFormat(platform, "24h") }
            menu.add(timeMenu)
            val tlMenu = JMenu("Timeline Quick View State")
            tlMenu.add("Active").addActionListener { setTimelineQuickView(platform, true) }
            tlMenu.add("Inactive").addActionListener { setTimelineQuickView(platform, false) }
            menu.add(tlMenu)
            menu.show(source, 0, -menu.preferredSize.height)
        }

        private fun showBatteryDialog(platform: String) {
            val spinner = JSpinner(SpinnerNumberModel(50, 0, 100, 1))
            val chargingCheckBox = JBCheckBox("is charging?")
            val dialog = object : DialogWrapper(project, true) {
                init {
                    title = "Battery & Charge"
                    init()
                }
                override fun createCenterPanel(): JComponent = JBPanel<JBPanel<*>>().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JBLabel("Battery Level (0-100):"))
                    add(Box.createVerticalStrut(4))
                    add(spinner)
                    add(Box.createVerticalStrut(8))
                    add(chargingCheckBox)
                }
            }
            if (!dialog.showAndGet()) return
            val level = spinner.value as Int
            val chargingOpt = if (chargingCheckBox.isSelected) " --charging" else ""
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProcessBuilder(
                        "bash", "-c",
                        "$pebblePath emu-battery --emulator $platform --vnc --percent $level$chargingOpt"
                    )
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {}
            }
        }

        private fun setTimelineQuickView(platform: String, active: Boolean) {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            val state = if (active) "on" else "off"
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProcessBuilder("bash", "-c",
                        "$pebblePath emu-set-timeline-quick-view --emulator $platform --vnc $state"
                    )
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {}
            }
        }

        private fun setTimeFormat(platform: String, format: String) {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProcessBuilder("bash", "-c",
                        "$pebblePath emu-time-format --emulator $platform --vnc --format $format"
                    )
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {}
            }
        }

        private fun emulateTap(platform: String) {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProcessBuilder("bash", "-c",
                        "$pebblePath emu-tap --emulator $platform --vnc --direction y+"
                    )
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {}
            }
        }

        private fun setBluetooth(platform: String, connected: Boolean) {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            val connectedStr = if (connected) "yes" else "no"
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    ProcessBuilder(
                        "bash", "-c",
                        "$pebblePath emu-bt-connection --emulator $platform --vnc --connected $connectedStr"
                    )
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                } catch (_: Exception) {}
            }
        }

        private fun wipeEmulator() {
            val emulatorWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Pebble Emulator") ?: return

            SwingUtilities.invokeLater {
                emulatorWindow.contentManager.removeAllContents(true)
                emulatorWindow.hide()
            }

            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val commandLine = GeneralCommandLine("bash", "-c", "$pebblePath kill && $pebblePath wipe")
                    val handler = OSProcessHandler(commandLine)
                    handler.startNotify()
                } catch (_: Exception) { }
            }
        }

        private fun findPbwFile(): File? {
            val buildDir = File(project.basePath ?: return null, "build")
            return buildDir.listFiles()?.firstOrNull { it.extension == "pbw" }
        }

        private fun runPebbleBuildSync(indicator: ProgressIndicator): Boolean {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            indicator.text = "Running pebble build..."
            return try {
                val process = ProcessBuilder("bash", "-c", "$pebblePath build")
                    .directory(File(project.basePath ?: return false))
                    .redirectErrorStream(true)
                    .start()
                process.waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }

        private fun saveFile(file: File, extension: String) {
            val descriptor = FileSaverDescriptor("Save File", "", extension)
            val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper = dialog.save(null as VirtualFile?, file.name) ?: return
            try {
                file.copyTo(wrapper.file, overwrite = true)
            } catch (e: Exception) {
                Messages.showErrorDialog(project, "Failed to save file: ${e.message}", "Save Error")
            }
        }

        private fun findOrBuildPbwFile(indicator: ProgressIndicator): File? {
            indicator.text = "Searching for PBW file..."
            findPbwFile()?.let { return it }
            if (!runPebbleBuildSync(indicator)) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, "pebble build failed. Cannot download PBW file.", "Download Error")
                }
                return null
            }
            val pbw = findPbwFile()
            if (pbw == null) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, "PBW file not found after build.", "Download Error")
                }
            }
            return pbw
        }

        private fun downloadPbw() {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing PBW file", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val pbwFile = findOrBuildPbwFile(indicator) ?: return
                    SwingUtilities.invokeLater { saveFile(pbwFile, "pbw") }
                }
            })
        }

        private fun downloadZip() {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing ZIP file", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
//                    findOrBuildPbwFile(indicator) ?: return
                    indicator.text = "Creating ZIP file..."
                    val projectDir = File(project.basePath ?: return)
                    val zipFile = File(System.getProperty("java.io.tmpdir"), "${projectDir.name}.zip")
                    try {
                        val process = ProcessBuilder("zip", "-r", zipFile.absolutePath, ".")
                            .directory(projectDir)
                            .redirectErrorStream(true)
                            .start()
                        if (process.waitFor() != 0) {
                            SwingUtilities.invokeLater {
                                Messages.showErrorDialog(project, "Failed to create ZIP file.", "Download Error")
                            }
                            return
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            Messages.showErrorDialog(project, "Failed to create ZIP: ${e.message}", "Download Error")
                        }
                        return
                    }
                    SwingUtilities.invokeLater { saveFile(zipFile, "zip") }
                }
            })
        }

        private fun screenshotEmulator() {
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            val platform = platforms[platformCombo.selectedItem as String] ?: "emery"
            runScreenshot("$pebblePath screenshot --emulator $platform --vnc")
        }

        private fun screenshotPhone() {
            val phoneIp = phoneIpField.text.trim()
            if (phoneIp.isEmpty()) {
                Messages.showErrorDialog(project, "Please enter a phone IP address.", "Screenshot Error")
                return
            }
            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            runScreenshot("$pebblePath screenshot --phone $phoneIp")
        }

        private fun runScreenshot(cmd: String) {
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val process = ProcessBuilder("bash", "-c", cmd)
                        .directory(File(project.basePath ?: return@executeOnPooledThread))
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText().trim()
                    val exitCode = process.waitFor()
                    SwingUtilities.invokeLater {
                        if (exitCode == 0) {
                            LocalFileSystem.getInstance()
                                .refreshAndFindFileByPath(project.basePath ?: "")
                                ?.refresh(true, false)
                        }
                    val (type, message) = if (exitCode == 0) {
                            NotificationType.INFORMATION to "Screenshot saved.\n$output"
                        } else {
                            NotificationType.ERROR to "Screenshot failed.\n$output"
                        }
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("PebbleAppFaceDevelopmentPluginUnOfficial")
                            .createNotification(message, type)
                            .notify(project)
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("PebbleAppFaceDevelopmentPluginUnOfficial")
                            .createNotification("Screenshot failed: ${e.message}", NotificationType.ERROR)
                            .notify(project)
                    }
                }
            }
        }

        private fun launchPhone() {
            val emulatorWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Pebble Emulator") ?: return

            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            val phoneIp = phoneIpField.text.trim()
            val logs = if (withLogsPhoneCheckBox.isSelected) " --logs" else ""
            val cmd = "$pebblePath build && $pebblePath install --phone $phoneIp$logs"

            val consoleView = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console

            val content = ContentFactory.getInstance()
                .createContent(consoleView.component, null, false)
            Disposer.register(content, consoleView)

            emulatorWindow.contentManager.removeAllContents(true)
            emulatorWindow.contentManager.addContent(content)
            emulatorWindow.show()

            try {
                val commandLine = GeneralCommandLine("bash", "-c", cmd)
                    .withWorkDirectory(project.basePath)
                val handler = OSProcessHandler(commandLine)
                consoleView.attachToProcess(handler)
                handler.startNotify()
            } catch (e: Exception) {
                consoleView.print(
                    "Failed to start: ${e.message}\n",
                    ConsoleViewContentType.ERROR_OUTPUT
                )
            }
        }

        private fun launchEmulator() {
            val emulatorWindow = ToolWindowManager.getInstance(project)
                .getToolWindow("Pebble Emulator") ?: return

            val pebblePath = PebbleSettings.getInstance().state.pebblePath
            val platform = platforms[platformCombo.selectedItem as String] ?: "emery"
            val logs = if (withLogsCheckBox.isSelected) " --logs" else ""
            val cmd = "pkill -x qemu-pebble; sleep 1; $pebblePath build && $pebblePath install --emulator $platform --vnc$logs"

            val consoleView = TextConsoleBuilderFactory.getInstance()
                .createBuilder(project)
                .console
            val vncPanel = PebbleVncPanel()

            val rightPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                add(createVncScrollPane(vncPanel), BorderLayout.CENTER)
                add(createEmulatorBottomBar(platform), BorderLayout.SOUTH)
            }

            val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rightPanel, consoleView.component).apply {
                resizeWeight = 0.3
                isContinuousLayout = true
            }

            vncPanel.onFirstFrame = {
                splitPane.setDividerLocation(0.3)
                splitPane.validate()
                splitPane.repaint()
            }

            val content = ContentFactory.getInstance()
                .createContent(splitPane, null, false)
            Disposer.register(content, consoleView)
            Disposer.register(content, Disposable { vncPanel.dispose() })

            emulatorWindow.contentManager.removeAllContents(true)
            emulatorWindow.contentManager.addContent(content)
            emulatorWindow.show()
            SwingUtilities.invokeLater { splitPane.setDividerLocation(0.3) }

            // Start retrying VNC connection immediately
            vncPanel.startConnection()

            // Start the build + install command
            try {
                val commandLine = GeneralCommandLine("bash", "-c", cmd)
                    .withWorkDirectory(project.basePath)
                val handler = OSProcessHandler(commandLine)
                consoleView.attachToProcess(handler)
                handler.startNotify()
            } catch (e: Exception) {
                consoleView.print(
                    "Failed to start: ${e.message}\n",
                    ConsoleViewContentType.ERROR_OUTPUT
                )
            }
        }

        private fun createNewProject() {
            val dialog = CreateProjectDialog()
            if (!dialog.showAndGet()) return

            val projectType = dialog.getSelectedType()
            val projectName = dialog.getProjectName()
            val parentPath = dialog.getParentPath()
            val pebblePath = PebbleSettings.getInstance().state.pebblePath

            ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Creating Pebble Project", false) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    indicator.text = "Running pebble new-project..."
                    try {
                        val flags = projectType.flag.split(" ")
                        val cmd = listOf(pebblePath, "new-project") + flags + listOf(projectName)
                        val process = ProcessBuilder(cmd)
                            .directory(File(parentPath))
                            .redirectErrorStream(true)
                            .start()
                        val output = process.inputStream.bufferedReader().readText()
                        val exitCode = process.waitFor()
                        if (exitCode == 0) {
                            File(parentPath, "$projectName/.gitignore").writeText(".idea/\n")
                            val ideaDir = File(parentPath, "$projectName/.idea")
                            ideaDir.mkdirs()
                            File(ideaDir, ".name").writeText(projectName)
                            File(ideaDir, "workspace.xml").writeText(
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n</project>"
                            )
                        }
                        SwingUtilities.invokeLater {
                            if (exitCode == 0) {
                                ProjectUtil.openOrImport(
                                    Path.of(parentPath, projectName),
                                    OpenProjectTask.build()
                                )
                            } else {
                                Messages.showErrorDialog(
                                    null as Project?,
                                    "Failed to create project:\n$output",
                                    "Create Project Error"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            Messages.showErrorDialog(null as Project?, "Error: ${e.message}", "Create Project Error")
                        }
                    }
                }
            })
        }

        private inner class CreateProjectDialog : DialogWrapper(project, true) {
            val projectTypes = listOf(
                ProjectTypeOption("C", "--c"),
                ProjectTypeOption("C and phone-side JS", "--c --javascript"),
                ProjectTypeOption("Alloy", "--alloy"),
                ProjectTypeOption("C with AI", "--c --ai")
            )

            val typeCombo = ComboBox(projectTypes.map { it.label }.toTypedArray())
            val nameField = JBTextField(30)
            val pathField = JBTextField(30)

            init {
                title = "Create New Pebble Project"
                pathField.text = System.getProperty("user.home")
                init()
            }

            override fun createCenterPanel(): JComponent {
                val browseButton = JButton("Browse...").apply {
                    addActionListener {
                        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        val vfsFile = FileChooser.chooseFile(descriptor, project, null)
                        vfsFile?.let { pathField.text = it.path }
                    }
                }
                val pathRow = JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    add(pathField, BorderLayout.CENTER)
                    add(browseButton, BorderLayout.EAST)
                }
                return JBPanel<JBPanel<*>>().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)

                    add(JBLabel("Project Type:"))
                    add(Box.createVerticalStrut(4))
                    typeCombo.alignmentX = 0.0f
                    add(typeCombo)

                    add(Box.createVerticalStrut(8))
                    add(JBLabel("Project Name:"))
                    add(Box.createVerticalStrut(4))
                    nameField.alignmentX = 0.0f
                    add(nameField)

                    add(Box.createVerticalStrut(8))
                    add(JBLabel("Parent Directory:"))
                    add(Box.createVerticalStrut(4))
                    pathRow.alignmentX = 0.0f
                    add(pathRow)
                }
            }

            override fun doValidate(): ValidationInfo? {
                if (nameField.text.isBlank()) return ValidationInfo("Project name cannot be empty", nameField)
                if (pathField.text.isBlank()) return ValidationInfo("Please select a parent directory", pathField)
                if (!File(pathField.text).isDirectory) return ValidationInfo("Selected path is not a valid directory", pathField)
                return null
            }

            fun getSelectedType() = projectTypes[typeCombo.selectedIndex]
            fun getProjectName() = nameField.text.trim()
            fun getParentPath() = pathField.text.trim()
        }
    }
}