package com.jetbrains.rider.plugins.sts2cardviewer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "Sts2CardViewerSettings",
    storages = [Storage("Sts2CardViewerSettings.xml")]
)
class Sts2CardViewerSettings : PersistentStateComponent<Sts2CardViewerSettings> {

    var gridTitleFontSize: Int = 13
    var gridDescFontSize: Int = 10
    var detailTitleFontSize: Int = 20
    var detailDescFontSize: Int = 13

    override fun getState(): Sts2CardViewerSettings = this

    override fun loadState(state: Sts2CardViewerSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): Sts2CardViewerSettings {
            return ApplicationManager.getApplication().getService(Sts2CardViewerSettings::class.java)
        }
    }
}
