package com.jetbrains.rider.plugins.sts2cardviewer

import com.intellij.openapi.options.Configurable
import javax.swing.*
import java.awt.*

class Sts2CardViewerConfigurable : Configurable {

    private var gridTitleFontSizeField: JSpinner? = null
    private var gridDescFontSizeField: JSpinner? = null
    private var detailTitleFontSizeField: JSpinner? = null
    private var detailDescFontSizeField: JSpinner? = null

    override fun getDisplayName(): String = "STS2 Card Viewer"

    override fun createComponent(): JComponent {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        }
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 8, 4, 8)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        val settings = Sts2CardViewerSettings.getInstance()

        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("Grid Card View:"), gbc)
        gbc.gridx = 1; gbc.gridy = 0
        panel.add(JLabel(""), gbc)

        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JLabel("  Title Font Size:"), gbc)
        gridTitleFontSizeField = JSpinner(SpinnerNumberModel(settings.gridTitleFontSize, 6, 30, 1))
        gbc.gridx = 1; gbc.gridy = 1
        panel.add(gridTitleFontSizeField!!, gbc)

        gbc.gridx = 0; gbc.gridy = 2
        panel.add(JLabel("  Description Font Size:"), gbc)
        gridDescFontSizeField = JSpinner(SpinnerNumberModel(settings.gridDescFontSize, 6, 20, 1))
        gbc.gridx = 1; gbc.gridy = 2
        panel.add(gridDescFontSizeField!!, gbc)

        gbc.gridx = 0; gbc.gridy = 3
        panel.add(JLabel(""), gbc)

        gbc.gridx = 0; gbc.gridy = 4
        panel.add(JLabel("Detail View:"), gbc)
        gbc.gridx = 1; gbc.gridy = 4
        panel.add(JLabel(""), gbc)

        gbc.gridx = 0; gbc.gridy = 5
        panel.add(JLabel("  Title Font Size:"), gbc)
        detailTitleFontSizeField = JSpinner(SpinnerNumberModel(settings.detailTitleFontSize, 8, 40, 1))
        gbc.gridx = 1; gbc.gridy = 5
        panel.add(detailTitleFontSizeField!!, gbc)

        gbc.gridx = 0; gbc.gridy = 6
        panel.add(JLabel("  Description Font Size:"), gbc)
        detailDescFontSizeField = JSpinner(SpinnerNumberModel(settings.detailDescFontSize, 8, 30, 1))
        gbc.gridx = 1; gbc.gridy = 6
        panel.add(detailDescFontSizeField!!, gbc)

        return panel
    }

    override fun isModified(): Boolean {
        val settings = Sts2CardViewerSettings.getInstance()
        return gridTitleFontSizeField?.value != settings.gridTitleFontSize ||
                gridDescFontSizeField?.value != settings.gridDescFontSize ||
                detailTitleFontSizeField?.value != settings.detailTitleFontSize ||
                detailDescFontSizeField?.value != settings.detailDescFontSize
    }

    override fun apply() {
        val settings = Sts2CardViewerSettings.getInstance()
        settings.gridTitleFontSize = (gridTitleFontSizeField?.value as? Number)?.toInt() ?: 11
        settings.gridDescFontSize = (gridDescFontSizeField?.value as? Number)?.toInt() ?: 9
        settings.detailTitleFontSize = (detailTitleFontSizeField?.value as? Number)?.toInt() ?: 20
        settings.detailDescFontSize = (detailDescFontSizeField?.value as? Number)?.toInt() ?: 13
    }

    override fun reset() {
        val settings = Sts2CardViewerSettings.getInstance()
        gridTitleFontSizeField?.value = settings.gridTitleFontSize
        gridDescFontSizeField?.value = settings.gridDescFontSize
        detailTitleFontSizeField?.value = settings.detailTitleFontSize
        detailDescFontSizeField?.value = settings.detailDescFontSize
    }
}
