package com.jetbrains.rider.plugins.sts2cardviewer.ui

import com.jetbrains.rider.plugins.sts2cardviewer.model.ModInfo
import com.jetbrains.rider.plugins.sts2cardviewer.model.RelicData
import com.jetbrains.rider.plugins.sts2cardviewer.parser.AssetPathResolver
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

class RelicListPanel : JPanel() {

    var onRelicSelected: ((RelicData) -> Unit)? = null
    private var currentModInfo: ModInfo? = null

    private val listPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UIManager.getColor("Panel.background")
    }

    private val scrollPane = JScrollPane(listPanel).apply {
        border = null
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val countLabel = JLabel("").apply {
        font = Font("Dialog", Font.PLAIN, 12)
        foreground = UIManager.getColor("Label.foreground")
        border = EmptyBorder(4, 8, 4, 8)
    }

    init {
        layout = BorderLayout()
        add(countLabel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
    }

    fun setRelics(relics: List<RelicData>, modInfo: ModInfo? = null, totalCount: Int = relics.size) {
        currentModInfo = modInfo
        listPanel.removeAll()

        countLabel.text = "${relics.size} of $totalCount"

        for (relic in relics) {
            listPanel.add(createRelicRow(relic))
            listPanel.add(Box.createRigidArea(Dimension(0, 4)))
        }

        listPanel.revalidate()
        listPanel.repaint()
    }

    private fun createRelicRow(relic: RelicData): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = Color(26, 26, 46)
            border = EmptyBorder(8, 12, 8, 12)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            maximumSize = Dimension(Integer.MAX_VALUE, 80)
        }

        val iconPanel = JPanel(BorderLayout()).apply {
            background = Color(15, 15, 26)
            preferredSize = Dimension(48, 48)
            minimumSize = Dimension(48, 48)
            maximumSize = Dimension(48, 48)
        }

        val modInfo = currentModInfo
        if (modInfo != null && relic.iconPath != null) {
            val fullPath = AssetPathResolver.resolveRelicIcon(modInfo.modRootPath, modInfo.modId, relic.iconPath)
            if (fullPath != null) {
                val image = AssetPathResolver.loadImage(fullPath)
                if (image != null) {
                    val scaled = image.getScaledInstance(48, 48, Image.SCALE_SMOOTH)
                    iconPanel.add(JLabel(ImageIcon(scaled)).apply {
                        horizontalAlignment = SwingConstants.CENTER
                        verticalAlignment = SwingConstants.CENTER
                    }, BorderLayout.CENTER)
                }
            }
        }

        val infoPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Color(26, 26, 46)
            border = EmptyBorder(0, 6, 0, 0)
        }

        val titleLabel = JLabel(relic.title).apply {
            font = Font("Dialog", Font.BOLD, 13)
            foreground = Color(221, 221, 221)
        }

        val rarityLabel = JLabel(relic.rarity.uppercase()).apply {
            font = Font("Dialog", Font.PLAIN, 9)
            foreground = when (relic.rarity.lowercase()) {
                "basic" -> Color(255, 255, 255)
                "common" -> Color(255, 255, 255)
                "uncommon" -> Color(255, 230, 66)
                "rare" -> Color(255, 107, 107)
                "token" -> Color(255, 255, 255)
                else -> Color(160, 160, 160)
            }
        }

        infoPanel.add(titleLabel)
        infoPanel.add(rarityLabel)

        panel.add(iconPanel, BorderLayout.WEST)
        panel.add(infoPanel, BorderLayout.CENTER)

        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                onRelicSelected?.invoke(relic)
            }
        })

        return panel
    }
}
