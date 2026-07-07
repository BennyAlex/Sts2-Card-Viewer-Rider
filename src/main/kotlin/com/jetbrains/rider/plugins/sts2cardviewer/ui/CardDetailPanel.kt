package com.jetbrains.rider.plugins.sts2cardviewer.ui

import com.jetbrains.rider.plugins.sts2cardviewer.model.CardData
import com.jetbrains.rider.plugins.sts2cardviewer.model.ModInfo
import com.jetbrains.rider.plugins.sts2cardviewer.Sts2CardViewerSettings
import com.jetbrains.rider.plugins.sts2cardviewer.parser.AssetPathResolver
import com.jetbrains.rider.plugins.sts2cardviewer.parser.CardMetadataExtractor
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext

class CardDetailPanel(private val project: Project) {

    val mainPanel: JPanel
    private val titleLabel = JLabel()
    private val costBadge = object : JPanel() {
        private var costText = ""
        private var energyIcon: Image? = null

        fun setCostText(text: String) {
            costText = text
            repaint()
        }

        fun setEnergyIcon(icon: Image?) {
            energyIcon = icon
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val icon = energyIcon
            if (icon != null) {
                val iw = icon.getWidth(null)
                val ih = icon.getHeight(null)
                val scale = minOf(width.toDouble() / iw, height.toDouble() / ih)
                val imgW = (iw * scale).toInt().coerceAtLeast(1)
                val imgH = (ih * scale).toInt().coerceAtLeast(1)
                val x = (width - imgW) / 2
                val y = (height - imgH) / 2
                g2.drawImage(icon, x, y, imgW, imgH, null)
            } else {
                g2.color = Color(59, 130, 246)
                g2.fillRoundRect(0, 0, width, height, 8, 8)
            }

            if (costText.isNotEmpty()) {
                val font = Font("Dialog", Font.BOLD, 16)
                g2.font = font
                val fm = g2.fontMetrics
                val tx = (width - fm.stringWidth(costText)) / 2f
                val ty = (height + fm.ascent - fm.descent) / 2f
                val textShape = g2.font.createGlyphVector(g2.fontRenderContext, costText).getOutline(tx, ty)
                g2.color = Color(0x22, 0x22, 0x22)
                g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.draw(textShape)
                g2.color = Color.WHITE
                g2.fill(textShape)
            }
        }
    }
    private val titleRow = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val typeRarityLabel = JLabel()
    private val descriptionLabel = JTextPane()
    
    // --- Editing Action Components ---
    private var rawDescription: String = ""
    private val editButton = JButton("Edit")
    private val saveButton = JButton("Save")
    private val discardButton = JButton("Discard")
    private val chooseImageButton = JButton("Choose Image")
    private val editActionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    // ----------------------------------

    private val statsLabel = JLabel()
    private val keywordsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
    private val detailsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val filePathLabel = JLabel()
    private val portraitLabel = JLabel()
    private val openSourceLink = JLabel()
    private val openImageLink = JLabel()
    var onImageChanged: ((className: String, isCard: Boolean) -> Unit)? = null
    private var currentCard: CardData? = null
    private var currentRelic: com.jetbrains.rider.plugins.sts2cardviewer.model.RelicData? = null
    private var currentModInfo: ModInfo? = null
    private var currentLanguage: String = "eng"

    init {
        mainPanel = JPanel(BorderLayout()).apply {
            background = UIManager.getColor("Panel.background")
            border = EmptyBorder(12, 16, 12, 12)

            val contentPanel = object : JPanel(), Scrollable {
                override fun getScrollableTracksViewportWidth() = true
                override fun getScrollableTracksViewportHeight() = false
                override fun getPreferredScrollableViewportSize() = preferredSize
                override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int) = 10
                override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int) = 10
            }.apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false

                portraitLabel.apply {
                    horizontalAlignment = SwingConstants.LEFT
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = EmptyBorder(0, 0, 6, 0)
                }

                titleLabel.apply {
                    font = Font("Dialog", Font.BOLD, 20)
                    foreground = Color(221, 221, 221)
                    horizontalAlignment = SwingConstants.LEFT
                }

                costBadge.apply {
                    preferredSize = Dimension(42, 42)
                    minimumSize = Dimension(42, 42)
                    maximumSize = Dimension(42, 42)
                    isOpaque = false
                }

                titleRow.add(costBadge)
                titleRow.add(Box.createHorizontalStrut(3))
                titleRow.add(titleLabel)

                titleLabel.border = EmptyBorder(0, 0, 0, 0)

                typeRarityLabel.apply {
                    font = Font("Dialog", Font.PLAIN, 12)
                    foreground = Color(160, 160, 160)
                    alignmentX = Component.LEFT_ALIGNMENT
                    horizontalAlignment = SwingConstants.LEFT
                }

                descriptionLabel.apply {
                    isEditable = false
                    isOpaque = false
                    border = null
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(350, Int.MAX_VALUE)
                }

                // --- Build Editor Controls ---
                editButton.apply {
                    font = Font("Dialog", Font.PLAIN, 11)
                    margin = Insets(2, 8, 2, 8)
                    addActionListener { enterEditMode() }
                }
                saveButton.apply {
                    font = Font("Dialog", Font.BOLD, 11)
                    margin = Insets(2, 8, 2, 8)
                    isVisible = false
                    addActionListener { saveEditedDescription() }
                }
                discardButton.apply {
                    font = Font("Dialog", Font.PLAIN, 11)
                    margin = Insets(2, 8, 2, 8)
                    isVisible = false
                    addActionListener { exitEditMode(discard = true) }
                }
                editActionsPanel.apply {
                    add(editButton)
                    add(saveButton)
                    add(Box.createHorizontalStrut(6))
                    add(discardButton)
                }
                // -----------------------------

                chooseImageButton.apply {
                    font = Font("Dialog", Font.PLAIN, 11)
                    margin = Insets(2, 8, 2, 8)
                    alignmentX = Component.LEFT_ALIGNMENT
                    isVisible = false
                    addActionListener { chooseImage() }
                }

                statsLabel.apply {
                    font = Font("Dialog", Font.PLAIN, 12)
                    foreground = Color(52, 211, 153)
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                keywordsPanel.apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(350, 60)
                }

                filePathLabel.apply {
                    font = Font("Dialog", Font.ITALIC, 9)
                    foreground = Color(100, 100, 100)
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                openSourceLink.apply {
                    font = Font("Dialog", Font.PLAIN, 13)
                    foreground = Color(88, 166, 255)
                    alignmentX = Component.LEFT_ALIGNMENT
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) { openSourceFile() }
                        override fun mouseEntered(e: MouseEvent?) { foreground = Color(120, 190, 255) }
                        override fun mouseExited(e: MouseEvent?) { foreground = Color(88, 166, 255) }
                    })
                }

                openImageLink.apply {
                    font = Font("Dialog", Font.PLAIN, 13)
                    foreground = Color(88, 166, 255)
                    alignmentX = Component.LEFT_ALIGNMENT
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent?) { openImageInExplorer() }
                        override fun mouseEntered(e: MouseEvent?) { foreground = Color(120, 190, 255) }
                        override fun mouseExited(e: MouseEvent?) { foreground = Color(88, 166, 255) }
                    })
                }

                detailsPanel.add(typeRarityLabel)
                detailsPanel.add(Box.createRigidArea(Dimension(0, 5)))
                detailsPanel.add(descriptionLabel)
                detailsPanel.add(Box.createRigidArea(Dimension(0, 5)))
                detailsPanel.add(editActionsPanel) // Injected edit bar
                detailsPanel.add(Box.createRigidArea(Dimension(0, 5)))
                detailsPanel.add(statsLabel)
                detailsPanel.add(Box.createRigidArea(Dimension(0, 5)))
                detailsPanel.add(keywordsPanel)

                add(portraitLabel)
                add(Box.createRigidArea(Dimension(0, 5)))
                add(chooseImageButton)
                add(Box.createRigidArea(Dimension(0, 5)))
                add(titleRow)
                add(detailsPanel)
                add(Box.createRigidArea(Dimension(0, 5)))
                add(openSourceLink)
                add(openImageLink)
                add(Box.createVerticalGlue())
                add(filePathLabel)
            }

            JScrollPane(contentPanel).apply {
                border = null
                viewportBorder = null
                isOpaque = false
                viewport.isOpaque = false
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            }.also { add(it, BorderLayout.CENTER) }
        }
    }

    fun showCard(card: CardData, modInfo: ModInfo?) {
        exitEditMode(discard = true)
        currentCard = card
        currentRelic = null
        currentModInfo = modInfo
        val settings = Sts2CardViewerSettings.getInstance()

        mainPanel.background = card.typeBadgeColor
        costBadge.setCostText(card.cost.toString())
        costBadge.setEnergyIcon(modInfo?.bigEnergyIconPath?.let { AssetPathResolver.loadImage(it) })
        costBadge.isVisible = true
        titleLabel.font = Font("Dialog", Font.BOLD, settings.detailTitleFontSize)
        titleLabel.text = card.title
        typeRarityLabel.text = buildString {
            append(card.type)
            if (card.target != null) append(" - ${formatTargetName(card.target)}")
            append(" - ${card.rarity}")
        }

        rawDescription = card.description ?: ""
        setStyledDescription(rawDescription, settings.detailDescFontSize)
        editActionsPanel.isVisible = true
        chooseImageButton.isVisible = true

        statsLabel.text = ""

        keywordsPanel.removeAll()
        for (kw in card.keywords) {
            val label = JLabel(kw).apply {
                font = Font("Dialog", Font.PLAIN, 10)
                foreground = Color(196, 181, 253)
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(139, 92, 246, 80), 1),
                    BorderFactory.createEmptyBorder(2, 6, 2, 6)
                )
                isOpaque = true
                background = Color(139, 92, 246, 30)
            }
            keywordsPanel.add(label)
        }
        keywordsPanel.revalidate()

        if (modInfo != null && card.portraitPath != null) {
            val fullPath = AssetPathResolver.resolveCardPortrait(modInfo.modRootPath, modInfo.modId, card.portraitPath)
            if (fullPath != null) {
                val image = AssetPathResolver.loadImage(fullPath)
                if (image != null) {
                    val viewWidth = mainPanel.width - 32
                    val maxW = if (viewWidth > 0) minOf(viewWidth, 300) else 300
                    val scale = maxW.toDouble() / image.width
                    val w = maxW
                    val h = (image.height * scale).toInt()
                    val scaled = image.getScaledInstance(w, h, Image.SCALE_SMOOTH)
                    portraitLabel.icon = ImageIcon(scaled)
                    portraitLabel.text = ""
                    portraitLabel.preferredSize = Dimension(w, h)
                    portraitLabel.minimumSize = Dimension(w, h)
                    portraitLabel.maximumSize = Dimension(w, h)
                } else {
                    portraitLabel.icon = null
                    portraitLabel.text = "No portrait"
                    portraitLabel.preferredSize = Dimension(0, 0)
                    portraitLabel.minimumSize = Dimension(0, 0)
                    portraitLabel.maximumSize = Dimension(32768, 32768)
                }
            } else {
                portraitLabel.icon = null
                portraitLabel.text = "No portrait"
                portraitLabel.preferredSize = Dimension(0, 0)
                portraitLabel.minimumSize = Dimension(0, 0)
                portraitLabel.maximumSize = Dimension(32768, 32768)
            }
        } else {
            portraitLabel.icon = null
            portraitLabel.text = "No portrait"
            portraitLabel.preferredSize = Dimension(0, 0)
            portraitLabel.minimumSize = Dimension(0, 0)
            portraitLabel.maximumSize = Dimension(32768, 32768)
        }

        filePathLabel.text = card.filePath?.let { "File: ${it.substringAfterLast('\\').substringAfterLast('/')}" } ?: ""
        openSourceLink.text = if (card.filePath != null) "Open source file" else ""
        openImageLink.text = if (card.portraitPath != null && modInfo != null) "Open image in explorer" else ""
    }

    fun showRelic(relic: com.jetbrains.rider.plugins.sts2cardviewer.model.RelicData, modInfo: ModInfo?) {
        exitEditMode(discard = true)
        currentCard = null
        currentRelic = relic
        currentModInfo = modInfo
        val settings = Sts2CardViewerSettings.getInstance()
        mainPanel.background = UIManager.getColor("Panel.background")
        costBadge.isVisible = false
        titleLabel.text = relic.title
        typeRarityLabel.text = "Relic - ${relic.rarity}"

        rawDescription = relic.description ?: ""
        setStyledDescription(rawDescription, settings.detailDescFontSize)
        editActionsPanel.isVisible = true
        chooseImageButton.isVisible = true

        statsLabel.text = ""
        keywordsPanel.removeAll()
        keywordsPanel.revalidate()

        if (modInfo != null && relic.iconPath != null) {
            val fullPath = AssetPathResolver.resolveRelicIcon(modInfo.modRootPath, modInfo.modId, relic.iconPath)
            if (fullPath != null) {
                val image = AssetPathResolver.loadImage(fullPath)
                if (image != null) {
                    val relicIconSize = 200
                    portraitLabel.icon = ImageIcon(image.getScaledInstance(relicIconSize, relicIconSize, Image.SCALE_SMOOTH))
                    portraitLabel.text = ""
                    portraitLabel.preferredSize = Dimension(relicIconSize, relicIconSize)
                    portraitLabel.minimumSize = Dimension(relicIconSize, relicIconSize)
                    portraitLabel.maximumSize = Dimension(relicIconSize, relicIconSize)
                } else {
                    portraitLabel.icon = null
                    portraitLabel.preferredSize = Dimension(0, 0)
                    portraitLabel.minimumSize = Dimension(0, 0)
                    portraitLabel.maximumSize = Dimension(32768, 32768)
                    portraitLabel.text = "No icon"
                }
            } else {
                portraitLabel.icon = null
                portraitLabel.text = "No icon"
                portraitLabel.preferredSize = Dimension(0, 0)
                portraitLabel.minimumSize = Dimension(0, 0)
                portraitLabel.maximumSize = Dimension(32768, 32768)
            }
        } else {
            portraitLabel.icon = null
            portraitLabel.text = "No icon"
            portraitLabel.preferredSize = Dimension(0, 0)
            portraitLabel.minimumSize = Dimension(0, 0)
            portraitLabel.maximumSize = Dimension(32768, 32768)
        }

        filePathLabel.text = relic.filePath?.let { "File: ${it.substringAfterLast('\\').substringAfterLast('/')}" } ?: ""
        openSourceLink.text = if (relic.filePath != null) "Open source file" else ""
        openImageLink.text = if (relic.iconPath != null && modInfo != null) "Open image in explorer" else ""
    }

    fun clear() {
        exitEditMode(discard = true)
        currentCard = null
        currentRelic = null
        currentModInfo = null
        mainPanel.background = UIManager.getColor("Panel.background")
        costBadge.isVisible = false
        titleLabel.text = ""
        typeRarityLabel.text = ""
        descriptionLabel.text = ""
        descriptionLabel.revalidate()
        descriptionLabel.repaint()
        editActionsPanel.isVisible = false
        chooseImageButton.isVisible = false
        statsLabel.text = ""
        keywordsPanel.removeAll()
        keywordsPanel.revalidate()
        filePathLabel.text = ""
        openSourceLink.text = ""
        openImageLink.text = ""
        portraitLabel.icon = null
        portraitLabel.text = ""
        portraitLabel.preferredSize = Dimension(0, 0)
        portraitLabel.minimumSize = Dimension(0, 0)
        portraitLabel.maximumSize = Dimension(32768, 32768)
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    fun currentCardFile(): String? = currentCard?.filePath
    fun currentRelicFile(): String? = currentRelic?.filePath
    fun setCurrentLanguage(langCode: String) { currentLanguage = langCode }

    private fun openSourceFile() {
        val filePath = currentCard?.filePath ?: currentRelic?.filePath ?: return
        val file = File(filePath)
        if (file.exists()) {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.absolutePath)
            if (virtualFile != null) {
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }
    }

    private fun openImageInExplorer() {
        val modInfo = currentModInfo ?: return
        val imagePath = when {
            currentCard != null -> {
                val card = currentCard!!
                AssetPathResolver.resolveCardPortrait(modInfo.modRootPath, modInfo.modId, card.portraitPath ?: "")
            }
            currentRelic != null -> {
                val relic = currentRelic!!
                AssetPathResolver.resolveRelicIcon(modInfo.modRootPath, modInfo.modId, relic.iconPath ?: "")
            }
            else -> null
        }
        if (imagePath != null) {
            val file = File(imagePath)
            if (file.exists()) {
                Desktop.getDesktop().open(file.parentFile)
            }
        }
    }

    private fun chooseImage() {
        val modInfo = currentModInfo ?: return
        val isCard = currentCard != null

        val fileChooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Image files", "png", "jpg", "jpeg", "gif", "bmp", "webp")
            dialogTitle = if (isCard) "Choose Card Portrait" else "Choose Relic Icon"
        }

        if (fileChooser.showOpenDialog(mainPanel) != JFileChooser.APPROVE_OPTION) return
        val sourceFile = fileChooser.selectedFile ?: return

        val entityName = currentCard?.className ?: currentRelic?.className ?: return
        val snakeName = CardMetadataExtractor.toSnakeCase(entityName)
        val targetFileName = "$snakeName.png"

        val rootDir = File(modInfo.modRootPath)
        val targetDir = if (isCard) {
            listOf(
                File(rootDir, "${modInfo.modId}/images/card_portraits"),
                File(rootDir, "images/card_portraits")
            ).firstOrNull { it.exists() || it.mkdirs() }
                ?: File(rootDir, "${modInfo.modId}/images/card_portraits").also { it.mkdirs() }
        } else {
            listOf(
                File(rootDir, "${modInfo.modId}/images/relics"),
                File(rootDir, "images/relics")
            ).firstOrNull { it.exists() || it.mkdirs() }
                ?: File(rootDir, "${modInfo.modId}/images/relics").also { it.mkdirs() }
        }

        val targetFile = File(targetDir, targetFileName)
        try {
            val image = ImageIO.read(sourceFile)
            if (image != null) {
                ImageIO.write(image, "png", targetFile)
            } else {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        } catch (e: Exception) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }

        LocalFileSystem.getInstance().refreshAndFindFileByPath(targetFile.absolutePath)
        onImageChanged?.invoke(entityName, isCard)
    }

    // --- Editor Control Implementations ---
    private fun enterEditMode() {
        descriptionLabel.isEditable = true
        descriptionLabel.isOpaque = true
        descriptionLabel.background = UIManager.getColor("TextField.background")
        descriptionLabel.foreground = UIManager.getColor("TextField.foreground")
        descriptionLabel.border = BorderFactory.createLineBorder(Color(100, 100, 100, 150), 1)
        
        // Show raw markup layout directly while working on edits
        descriptionLabel.text = rawDescription
        
        editButton.isVisible = false
        saveButton.isVisible = true
        discardButton.isVisible = true
        
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun exitEditMode(discard: Boolean) {
        descriptionLabel.isEditable = false
        descriptionLabel.isOpaque = false
        descriptionLabel.background = null
        descriptionLabel.border = null
        
        val settings = Sts2CardViewerSettings.getInstance()
        if (discard) {
            setStyledDescription(rawDescription, settings.detailDescFontSize)
        }
        
        editButton.isVisible = true
        saveButton.isVisible = false
        discardButton.isVisible = false
        
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun saveEditedDescription() {
        val cleanText = descriptionLabel.text ?: ""
        rawDescription = cleanText
        
        // Update local object states immediately so view doesn't revert context
        currentCard?.let { try { it.javaClass.getMethod("setDescription", String::class.java).invoke(it, cleanText) } catch(e: Exception) {} }
        currentRelic?.let { try { it.javaClass.getMethod("setDescription", String::class.java).invoke(it, cleanText) } catch(e: Exception) {} }
        
        saveDescriptionToJSON(cleanText)
        exitEditMode(discard = false)
        
        val settings = Sts2CardViewerSettings.getInstance()
        setStyledDescription(rawDescription, settings.detailDescFontSize)
    }

    private fun saveDescriptionToJSON(newDesc: String) {
        val modInfo = currentModInfo ?: return
        val isCard = currentCard != null
        val fileName = if (isCard) "cards.json" else "relics.json"
        
        val rootDir = File(modInfo.modRootPath)
        val langDirCandidates = listOf(
            File(rootDir, "${modInfo.modId}/localization/$currentLanguage"),
            File(rootDir, "localization/$currentLanguage")
        )
        var jsonFile = langDirCandidates.firstOrNull { File(it, fileName).exists() }?.let { File(it, fileName) }
        if (jsonFile == null) {
            jsonFile = locateJsonFile(rootDir, fileName) ?: File(rootDir, "src/main/resources/localization/$currentLanguage/$fileName")
        }
        
        if (!jsonFile.exists()) return

        try {
            // Leverage bundled IntelliJ platform GSON parser engine 
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
            val typeToken = object : com.google.gson.reflect.TypeToken<MutableMap<String, Any>>() {}.type
            val dataMap: MutableMap<String, Any> = gson.fromJson(jsonFile.readText(), typeToken) ?: mutableMapOf()

            val entityName = currentCard?.title ?: currentRelic?.title ?: return
            val sanitizedNodeName = entityName.uppercase().replace(" ", "_").replace("'", "")
            val sanitizedModName = modInfo.modId.uppercase().replace(" ", "_").replace("'", "")
            val targetKey = "$sanitizedModName-$sanitizedNodeName.description"

            dataMap[targetKey] = newDesc
            jsonFile.writeText(gson.toJson(dataMap))
            
            // Notify VFS so the workspace structural cache reflects file edits
            LocalFileSystem.getInstance().refreshAndFindFileByPath(jsonFile.absolutePath)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun locateJsonFile(directory: File, targetName: String): File? {
        if (!directory.isDirectory) return null
        val listing = directory.listFiles() ?: return null
        for (item in listing) {
            if (item.isDirectory) {
                val discovery = locateJsonFile(item, targetName)
                if (discovery != null) return discovery
            } else if (item.name.equals(targetName, ignoreCase = true)) {
                return item
            }
        }
        return null
    }
    // --------------------------------------

    companion object {
        private val COLOR_MAP = mapOf(
            "gold" to Color(0xFF, 0xD7, 0x00),
            "blue" to Color(0x3B, 0x82, 0xF6)
        )
        private val TAG_REGEX = Regex("\\[(gold|blue)\\](.*?)\\[/\\1\\]")

        fun setStyledText(textPane: JTextPane, text: String, fontSize: Int, defaultForeground: Color = Color(0xcc, 0xcc, 0xcc)) {
            val doc = textPane.styledDocument
            doc.remove(0, doc.length)
            val baseStyle = doc.addStyle("default", null).apply {
                StyleConstants.setFontFamily(this, "Dialog")
                StyleConstants.setFontSize(this, fontSize)
                StyleConstants.setForeground(this, defaultForeground)
                StyleConstants.setAlignment(this, StyleConstants.ALIGN_LEFT)
            }
            val styles = COLOR_MAP.mapValues { (_, color) ->
                doc.addStyle("color_${color}", baseStyle).apply {
                    StyleConstants.setForeground(this, color)
                }
            }
            var lastEnd = 0
            for (match in TAG_REGEX.findAll(text)) {
                if (match.range.first > lastEnd) {
                    doc.insertString(doc.length, text.substring(lastEnd, match.range.first), baseStyle)
                }
                val style = styles[match.groupValues[1]] ?: baseStyle
                doc.insertString(doc.length, match.groupValues[2], style)
                lastEnd = match.range.last + 1
            }
            if (lastEnd < text.length) {
                doc.insertString(doc.length, text.substring(lastEnd), baseStyle)
            }
            if (doc.length == 0) {
                doc.insertString(0, " ", baseStyle)
            }
            if (textPane.width > 0) textPane.setSize(textPane.width, Int.MAX_VALUE)
        }
    }

    private fun setStyledDescription(text: String, fontSize: Int) {
        setStyledText(descriptionLabel, text.replace("\\n", "\n"), fontSize)
        descriptionLabel.revalidate()
        descriptionLabel.repaint()
        mainPanel.revalidate()
        mainPanel.repaint()
    }
}