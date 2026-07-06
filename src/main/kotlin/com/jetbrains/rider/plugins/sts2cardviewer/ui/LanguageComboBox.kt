package com.jetbrains.rider.plugins.sts2cardviewer.ui

import com.jetbrains.rider.plugins.sts2cardviewer.model.LanguageInfo
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComboBox
import javax.swing.SwingUtilities

class LanguageComboBox {

    val comboBox: JComboBox<String>
    var onLanguageChanged: ((langCode: String) -> Unit)? = null

    private val languages = mutableListOf<LanguageInfo>()

    init {
        comboBox = JComboBox<String>().apply {
            preferredSize = java.awt.Dimension(120, 28)
            toolTipText = "Select localization language"
            addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent?) {
                    val selected = selectedItem as? String ?: return
                    val lang = languages.find { it.name == selected }
                    if (lang != null) {
                        onLanguageChanged?.invoke(lang.code)
                    }
                }
            })
        }
    }

    fun setLanguages(langs: List<LanguageInfo>) {
        languages.clear()
        languages.addAll(langs)

        SwingUtilities.invokeLater {
            comboBox.removeAllItems()
            for (lang in langs) {
                comboBox.addItem(lang.name)
            }
            if (langs.isNotEmpty()) {
                comboBox.selectedIndex = 0
            }
        }
    }

    fun getCurrentLanguageCode(): String? {
        val selected = comboBox.selectedItem as? String ?: return null
        return languages.find { it.name == selected }?.code
    }
}
