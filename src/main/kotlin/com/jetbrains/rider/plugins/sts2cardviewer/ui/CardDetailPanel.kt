package com.jetbrains.rider.plugins.sts2cardviewer.ui

import com.jetbrains.rider.plugins.sts2cardviewer.model.CardData
import com.jetbrains.rider.plugins.sts2cardviewer.model.ModInfo
import com.jetbrains.rider.plugins.sts2cardviewer.Sts2CardViewerSettings
import com.jetbrains.rider.plugins.sts2cardviewer.parser.AssetPathResolver
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
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
    private var currentCard: CardData? = null
    private var currentRelic: com.jetbrains.rider.plugins.sts2cardviewer.model.RelicData? = null
    private var currentModInfo: ModInfo? = null

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
                    isOpaque = false // Makes the background context color match the parent card color perfectly
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
                }

                statsLabel.apply {
                    font = Font("Dialog", Font.PLAIN, 12)
                    foreground = Color(52, 211, 153)
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                keywordsPanel.apply {
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(250, 60)
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
                        override fun mouseClicked(e: MouseEvent?) {
                            openSourceFile()
                        }
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
                        override fun mouseClicked(e: MouseEvent?) {
                            openImageInExplorer()
                        }
                        override fun mouseEntered(e: MouseEvent?) { foreground = Color(120, 190, 255) }
                        override fun mouseExited(e: MouseEvent?) { foreground = Color(88, 166, 255) }
                    })
                }

                detailsPanel.add(typeRarityLabel)
                detailsPanel.add(Box.createRigidArea(Dimension(0, 8)))
                detailsPanel.add(descriptionLabel)
                detailsPanel.add(Box.createRigidArea(Dimension(0, 8)))
                detailsPanel.add(statsLabel)
                detailsPanel.add(Box.createRigidArea(Dimension(0, 4)))
                detailsPanel.add(keywordsPanel)

                add(portraitLabel)
                add(titleRow)
                add(detailsPanel)
                add(Box.createRigidArea(Dimension(0, 8)))
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

        setStyledDescription(card.description, settings.detailDescFontSize)

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
        currentCard = null
        currentRelic = relic
        currentModInfo = modInfo
        val settings = Sts2CardViewerSettings.getInstance()
        mainPanel.background = UIManager.getColor("Panel.background")
        costBadge.isVisible = false
        titleLabel.text = relic.title
        typeRarityLabel.text = "Relic - ${relic.rarity}"

        setStyledDescription(relic.description, settings.detailDescFontSize)

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