package com.jetbrains.rider.plugins.sts2cardviewer.ui

import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.util.Vector

fun formatTargetName(target: String): String {
    return target.replace(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) { " " }
}

class FilterToolbar {

    val mainPanel: JPanel
    var onFilterChanged: ((text: String, type: String, rarity: String, target: String, keyword: String) -> Unit)? = null

    private val searchField = JTextField(20)
    private val typeCombo = JComboBox(arrayOf("All", "Attack", "Skill", "Power"))
    private val rarityCombo = JComboBox(arrayOf("All", "Basic", "Common", "Uncommon", "Rare", "Ancient", "Token"))
    private val targetCombo = JComboBox(arrayOf("All"))
    private val keywordCombo = JComboBox(arrayOf("All"))
    private var debounceTimer: Timer? = null

    init {
        mainPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            background = UIManager.getColor("Panel.background")

            searchField.apply {
                preferredSize = Dimension(200, 28)
                toolTipText = "Search cards by name, class, or description"
                document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) = debounceFilter()
                    override fun removeUpdate(e: DocumentEvent?) = debounceFilter()
                    override fun changedUpdate(e: DocumentEvent?) = debounceFilter()
                })
            }

            typeCombo.apply {
                preferredSize = Dimension(135, 28)
                toolTipText = "Filter by card type"
                addActionListener { fireFilter() }
            }

            rarityCombo.apply {
                preferredSize = Dimension(135, 28)
                toolTipText = "Filter by rarity"
                addActionListener { fireFilter() }
            }

            targetCombo.apply {
                preferredSize = Dimension(135, 28)
                toolTipText = "Filter by target"
                addActionListener { fireFilter() }
            }

            keywordCombo.apply {
                preferredSize = Dimension(135, 28)
                toolTipText = "Filter by keyword"
                addActionListener { fireFilter() }
            }

            add(JLabel("Search:").apply {
                font = Font("Dialog", Font.PLAIN, 11)
                foreground = Color(160, 160, 160)
            })
            add(searchField)
            add(JLabel("Type:").apply {
                font = Font("Dialog", Font.PLAIN, 11)
                foreground = Color(160, 160, 160)
            })
            add(typeCombo)
            add(JLabel("Rarity:").apply {
                font = Font("Dialog", Font.PLAIN, 11)
                foreground = Color(160, 160, 160)
            })
            add(rarityCombo)
            add(JLabel("Target:").apply {
                font = Font("Dialog", Font.PLAIN, 11)
                foreground = Color(160, 160, 160)
            })
            add(targetCombo)
            add(JLabel("Tag:").apply {
                font = Font("Dialog", Font.PLAIN, 11)
                foreground = Color(160, 160, 160)
            })
            add(keywordCombo)
        }
    }

    fun setKeywords(keywords: List<String>) {
        val current = keywordCombo.selectedItem as? String
        val items = Vector<String>()
        items.add("All")
        for (kw in keywords.sorted()) {
            items.add(kw)
        }
        keywordCombo.model = DefaultComboBoxModel(items)
        if (current != null && keywords.any { it.equals(current, ignoreCase = true) }) {
            keywordCombo.selectedItem = current
        }
    }

    fun setTargets(targets: List<String>) {
        val current = targetCombo.selectedItem as? String
        val items = Vector<String>()
        items.add("All")
        for (t in targets.sorted()) {
            items.add(t)
        }
        targetCombo.model = DefaultComboBoxModel(items)
        targetCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val v = value as? String ?: return c
                text = if (v == "All") v else formatTargetName(v)
                return c
            }
        }
        if (current != null && targets.any { it.equals(current, ignoreCase = true) }) {
            targetCombo.selectedItem = current
        }
    }

    private fun debounceFilter() {
        debounceTimer?.stop()
        debounceTimer = Timer(300) { fireFilter() }.apply {
            isRepeats = false
            start()
        }
    }

    private fun fireFilter() {
        debounceTimer?.stop()
        val text = searchField.text ?: ""
        val type = typeCombo.selectedItem as? String ?: "All"
        val rarity = rarityCombo.selectedItem as? String ?: "All"
        val target = targetCombo.selectedItem as? String ?: "All"
        val keyword = keywordCombo.selectedItem as? String ?: "All"
        onFilterChanged?.invoke(text, type, rarity, target, keyword)
    }
}
