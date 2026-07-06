package com.jetbrains.rider.plugins.sts2cardviewer.ui

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.rider.plugins.sts2cardviewer.Sts2CardViewerSettings
import com.jetbrains.rider.plugins.sts2cardviewer.model.CardData
import com.jetbrains.rider.plugins.sts2cardviewer.model.ModInfo
import com.jetbrains.rider.plugins.sts2cardviewer.parser.AssetPathResolver
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.AbstractBorder
import javax.swing.text.View
import java.awt.geom.RoundRectangle2D
import javax.swing.text.*

class WrapEditorKit : StyledEditorKit() {
    private val defaultFactory = WrapColumnFactory()
    override fun getViewFactory(): ViewFactory = defaultFactory
}

class WrapColumnFactory : ViewFactory {
    override fun create(elem: Element): View {
        return when (elem.name) {
            AbstractDocument.ContentElementName -> WrapLabelView(elem)
            AbstractDocument.ParagraphElementName -> ParagraphView(elem)
            AbstractDocument.SectionElementName -> BoxView(elem, View.Y_AXIS)
            StyleConstants.ComponentElementName -> ComponentView(elem)
            StyleConstants.IconElementName -> IconView(elem)
            else -> LabelView(elem)
        }
    }
}

class WrapLabelView(elem: Element) : LabelView(elem) {
    override fun getBreakWeight(axis: Int, pos: Float, len: Float): Int {
        if (axis == View.X_AXIS) {
            checkPainter()
            val p0 = startOffset
            val p1 = glyphPainter.getBoundedPosition(this, p0, pos, len)
            if (p1 == p0) return View.BadBreakWeight
            // The magic line: tells Swing it's okay to break mid-word
            return View.GoodBreakWeight 
        }
        return super.getBreakWeight(axis, pos, len)
    }

    override fun breakView(axis: Int, p0: Int, pos: Float, len: Float): View {
        if (axis == View.X_AXIS) {
            checkPainter()
            val p1 = glyphPainter.getBoundedPosition(this, p0, pos, len)
            if (p0 == startOffset && p1 == endOffset) return this
            return createFragment(p0, p1)
        }
        return super.breakView(axis, p0, pos, len)
    }
}

class CardGridPanel : JPanel() {

    private class RoundedBorder(private val color: Color, private val thickness: Int, private val radius: Int) : AbstractBorder() {
        override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.stroke = BasicStroke(thickness.toFloat())
            g2.draw(RoundRectangle2D.Double(x.toDouble(), y.toDouble(), width - 1.0, height - 1.0, radius.toDouble(), radius.toDouble()))
        }

        override fun getBorderInsets(c: Component) = Insets(thickness, thickness, thickness, thickness)
        override fun isBorderOpaque() = false
    }

    private val LOG = Logger.getInstance(CardGridPanel::class.java)

    var onCardSelected: ((CardData) -> Unit)? = null
    private var currentModInfo: ModInfo? = null
    private var currentColumns = 0
    private var currentFilteredList: List<CardData>? = null

    private val cardCellMap = LinkedHashMap<String, Pair<CardData, JPanel>>()
    private var cachedEnergyIcon: Image? = null
    private var cachedEnergyIconModId: String? = null

    private val gridPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIManager.getColor("Panel.background")
    }

    private val scrollPane = JScrollPane(gridPanel).apply {
        border = null
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)

        scrollPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                relayout(currentFilteredList ?: emptyList())
            }
        })
    }

    fun setCards(cards: List<CardData>, modInfo: ModInfo? = null) {
        currentModInfo = modInfo

        val modId = modInfo?.modId
        if (cachedEnergyIcon == null || cachedEnergyIconModId != modId) {
            cachedEnergyIcon = modInfo?.bigEnergyIconPath?.let { AssetPathResolver.loadImage(it) }
            cachedEnergyIconModId = modId
            LOG.warn("[STS2CardViewer] Cached energy icon: ${cachedEnergyIcon != null}")
        }

        for (card in cards) {
            val existing = cardCellMap[card.className]
            if (existing == null || existing.first != card) {
                cardCellMap[card.className] = Pair(card, createCardCell(card))
            }
        }

        currentColumns = 0
        currentFilteredList = cards
        relayout(cards)
    }

    private fun relayout(visibleCards: List<CardData>) {
        val availableWidth = scrollPane.viewport.width
        if (availableWidth <= 0) return

        val cellWidth = 221
        val columns = maxOf(1, availableWidth / cellWidth)
        if (columns == currentColumns && gridPanel.componentCount > 0) return
        currentColumns = columns

        gridPanel.removeAll()

        var rowPanel = JPanel(GridBagLayout()).apply {
            background = UIManager.getColor("Panel.background")
        }
        var colCount = 0

        for (card in visibleCards) {
            val (_, cell) = cardCellMap[card.className] ?: continue
            val gbc = GridBagConstraints().apply {
                insets = Insets(8, 8, 8, 8)
                anchor = GridBagConstraints.NORTHWEST
                fill = GridBagConstraints.VERTICAL // Force components to stretch down to match row height
                weighty = 1.0 // Allow vertical distribution
            }
            gbc.gridx = colCount
            gbc.gridy = 0
            rowPanel.add(cell, gbc)
            colCount++

            if (colCount == columns) {
                gridPanel.add(rowPanel)
                gridPanel.add(Box.createRigidArea(Dimension(0, 4)))
                rowPanel = JPanel(GridBagLayout()).apply {
                    background = UIManager.getColor("Panel.background")
                }
                colCount = 0
            }
        }

        if (colCount > 0) {
            for (i in colCount until columns) {
                val gbc = GridBagConstraints().apply {
                    gridx = i
                    gridy = 0
                    anchor = GridBagConstraints.NORTHWEST
                }
                rowPanel.add(Box.createRigidArea(Dimension(205, 1)), gbc)
            }
            gridPanel.add(rowPanel)
            gridPanel.add(Box.createRigidArea(Dimension(0, 8)))
        }

        // Fix 2: Pushes all horizontal rows up to the top of the container
        gridPanel.add(Box.createVerticalGlue())

        gridPanel.revalidate()
        gridPanel.repaint()
    }

    private fun createCostBadge(card: CardData): JComponent {
        val badgeSize = 38
        val image = cachedEnergyIcon
        if (image != null) {
            val iw = image.getWidth(null)
            val ih = image.getHeight(null)
            val scale = minOf(badgeSize.toDouble() / iw, badgeSize.toDouble() / ih)
            val w = (iw * scale).toInt().coerceAtLeast(1)
            val h = (ih * scale).toInt().coerceAtLeast(1)
            val scaled = image.getScaledInstance(w, h, Image.SCALE_SMOOTH)
            val badge = object : JPanel() {
                override fun getPreferredSize() = Dimension(badgeSize, badgeSize)
                override fun getMinimumSize() = Dimension(badgeSize, badgeSize)
                override fun getMaximumSize() = Dimension(badgeSize, badgeSize)
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    val imgW = scaled.getWidth(null)
                    val imgH = scaled.getHeight(null)
                    val x = (width - imgW) / 2
                    val y = (height - imgH) / 2
                    g2.drawImage(scaled, x, y, null)
                    g2.font = Font("Dialog", Font.BOLD, 14)
                    val text = card.cost.toString()
                    val fm = g2.fontMetrics
                    val tx = (width - fm.stringWidth(text)) / 2f
                    val ty = (height + fm.ascent - fm.descent) / 2f
                    val textShape = g2.font.createGlyphVector(g2.fontRenderContext, text).getOutline(tx, ty)
                    g2.color = Color(0x22, 0x22, 0x22)
                    g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g2.draw(textShape)
                    g2.color = Color.WHITE
                    g2.fill(textShape)
                }
            }.apply {
                isOpaque = false
                border = null
            }
            return badge
        }

        return object : JPanel() {
            override fun getPreferredSize() = Dimension(badgeSize, badgeSize)
            override fun getMinimumSize() = Dimension(badgeSize, badgeSize)
            override fun getMaximumSize() = Dimension(badgeSize, badgeSize)
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.color = Color(59, 130, 246)
                g2.fillRoundRect(0, 0, width, height, 8, 8)
                g2.font = Font("Dialog", Font.BOLD, 16)
                val text = card.cost.toString()
                val fm = g2.fontMetrics
                val tx = (width - fm.stringWidth(text)) / 2f
                val ty = (height + fm.ascent - fm.descent) / 2f
                val textShape = g2.font.createGlyphVector(g2.fontRenderContext, text).getOutline(tx, ty)
                g2.color = Color(0x22, 0x22, 0x22)
                g2.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.draw(textShape)
                g2.color = Color.WHITE
                g2.fill(textShape)
                g2.dispose()
            }
        }
    }

    private fun getRarityBorderColor(card: CardData): Color {
        return when (card.rarity.lowercase()) {
            "basic" -> Color(180, 180, 180)
            "common" -> Color(180, 180, 180)
            "uncommon" -> Color(50, 150, 220)
            "rare" -> Color(255, 215, 0)
            "token" -> Color(180, 180, 180)
            "ancient" -> Color(192, 132, 252)
            else -> Color(74, 54, 102)
        }
    }

    private fun getPortraitDimensions(): Pair<Int, Int> {
        return Pair(205, 135)
    }

    private fun createCardCell(card: CardData): JPanel {
        val panel = object : JPanel() {
            override fun getPreferredSize(): Dimension {
                val base = layout.preferredLayoutSize(this)
                return Dimension(221, maxOf(320, base.height))
            }
            override fun getMinimumSize(): Dimension {
                val base = layout.minimumLayoutSize(this)
                return Dimension(221, maxOf(320, base.height))
            }
            override fun getMaximumSize(): Dimension {
                // Uncap the maximum height so BoxLayout doesn't crush the panel
                return Dimension(221, Short.MAX_VALUE.toInt())
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = card.typeBadgeColor
            isOpaque = true
            
            border = BorderFactory.createCompoundBorder(
                RoundedBorder(getRarityBorderColor(card), 3, 14),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
            )
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = javax.swing.border.EmptyBorder(2, 2, 2, 2)
        }

        val costBadge = createCostBadge(card)
        val rarityBorderColor = getRarityBorderColor(card)
        val settings = Sts2CardViewerSettings.getInstance()

        val titleLabel = JLabel(card.title).apply {
            font = Font("Dialog", Font.BOLD, settings.gridTitleFontSize)
            foreground = Color(238, 238, 238)
            horizontalAlignment = SwingConstants.LEFT
            border = javax.swing.border.EmptyBorder(0, 3, 0, 0)
        }

        header.add(costBadge, BorderLayout.WEST)
        header.add(titleLabel, BorderLayout.CENTER)

        val portrait = loadPortrait(card)

        val typeRarityRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = javax.swing.border.EmptyBorder(3, 9, 3, 9)
            alignmentX = Component.CENTER_ALIGNMENT
        }

        val typeLabel = JLabel(card.type.uppercase()).apply {
            font = Font("Dialog", Font.BOLD, settings.gridDescFontSize)
            foreground = Color(220, 220, 220)
        }

        val rarityLabel = JLabel(card.rarity.uppercase()).apply {
            font = Font("Dialog", Font.BOLD, settings.gridDescFontSize)
            foreground = rarityBorderColor
            horizontalAlignment = SwingConstants.RIGHT
        }

        typeRarityRow.add(typeLabel, BorderLayout.WEST)
        if (card.target != null) {
            typeRarityRow.add(JLabel(formatTargetName(card.target).uppercase()).apply {
                font = Font("Dialog", Font.BOLD, settings.gridDescFontSize)
                foreground = Color(220, 220, 220)
                horizontalAlignment = SwingConstants.CENTER
            }, BorderLayout.CENTER)
        }
        typeRarityRow.add(rarityLabel, BorderLayout.EAST)

        // Using BorderLayout for the info panel to prevent squishing
        val info = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = javax.swing.border.EmptyBorder(2, 14, 2, 14)
            alignmentX = Component.CENTER_ALIGNMENT 
        }

        val descLabel = object : JTextPane() {
            override fun getPreferredSize(): Dimension {
                val fixedWidth = 193
                setSize(fixedWidth, Short.MAX_VALUE.toInt())
                return Dimension(fixedWidth, super.getPreferredSize().height)
            }
            override fun getMaximumSize() = Dimension(193, Short.MAX_VALUE.toInt())
        }.apply {
            // Apply our custom word-wrapping logic here
            editorKit = WrapEditorKit() 
            
            isEditable = false
            isOpaque = false
            // ... rest of your setup ...
        }
        
        CardDetailPanel.setStyledText(descLabel, card.description, settings.gridDescFontSize + 2)
        
        info.add(descLabel, BorderLayout.NORTH)

        if (card.keywords.isNotEmpty()) {
            // 1. Switch to BoxLayout (X_AXIS) to remove the automatic outer left-margin
            val kwPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                border = javax.swing.border.EmptyBorder(3, 0, 0, 0)
                alignmentX = Component.LEFT_ALIGNMENT
            }
            
            for ((index, kw) in card.keywords.withIndex()) {
                // 2. Add an exact 8px gap BEFORE every tag except the first one
                if (index > 0) {
                    kwPanel.add(Box.createHorizontalStrut(4))
                }
                
                kwPanel.add(JLabel(kw).apply {
                    font = Font("Dialog", Font.BOLD, settings.gridDescFontSize - 1)
                    foreground = Color(0, 0, 0)
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Color(220, 220, 220), 1),
                        BorderFactory.createEmptyBorder(1, 4, 1, 4)
                    )
                    isOpaque = true
                    background = Color(220, 220, 220, 210)
                })
            }
            // Align the panel to the west/left in the BorderLayout
            info.add(kwPanel, BorderLayout.SOUTH)
        }

        panel.add(header)
        panel.add(portrait)
        panel.add(typeRarityRow)
        panel.add(info)

        val clickHandler = { e: MouseEvent ->
            if (SwingUtilities.isLeftMouseButton(e)) onCardSelected?.invoke(card)
        }
        panel.addMouseListener(object : MouseAdapter() { override fun mouseClicked(e: MouseEvent) = clickHandler(e) })
        info.addMouseListener(object : MouseAdapter() { override fun mouseClicked(e: MouseEvent) = clickHandler(e) })
        typeRarityRow.addMouseListener(object : MouseAdapter() { override fun mouseClicked(e: MouseEvent) = clickHandler(e) })

        return panel
    }
    private fun loadPortrait(card: CardData): JPanel {
        val (portraitW, portraitH) = getPortraitDimensions()
        val portrait = JPanel().apply {
            isOpaque = false
            preferredSize = Dimension(portraitW, portraitH)
            minimumSize = Dimension(portraitW, portraitH)
            maximumSize = Dimension(portraitW, portraitH)
            layout = BorderLayout()
        }

        val modInfo = currentModInfo
        if (modInfo != null && card.portraitPath != null) {
            val fullPath = AssetPathResolver.resolveCardPortrait(modInfo.modRootPath, modInfo.modId, card.portraitPath)
            if (fullPath != null) {
                val image = AssetPathResolver.loadImage(fullPath)
                if (image != null) {
                    val scale = minOf(portraitW.toDouble() / image.width, portraitH.toDouble() / image.height)
                    val w = (image.width * scale).toInt()
                    val h = (image.height * scale).toInt()
                    val scaled = image.getScaledInstance(w, h, Image.SCALE_SMOOTH)
                    portrait.add(JLabel(ImageIcon(scaled)).apply {
                        horizontalAlignment = SwingConstants.CENTER
                        verticalAlignment = SwingConstants.CENTER
                    }, BorderLayout.CENTER)
                }
            }
        }

        return portrait
    }
}