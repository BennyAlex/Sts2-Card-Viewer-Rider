package com.jetbrains.rider.plugins.sts2cardviewer

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.jetbrains.rider.plugins.sts2cardviewer.ui.Sts2CardViewerPanel

class Sts2ToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = Sts2CardViewerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    @Suppress("DEPRECATION")
    override fun isApplicable(project: Project): Boolean {
        return true
    }
}
