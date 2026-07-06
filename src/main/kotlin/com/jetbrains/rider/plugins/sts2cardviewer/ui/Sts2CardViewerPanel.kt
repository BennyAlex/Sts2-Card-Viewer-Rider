package com.jetbrains.rider.plugins.sts2cardviewer.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.AsyncFileListener
import com.jetbrains.rider.plugins.sts2cardviewer.model.*
import com.jetbrains.rider.plugins.sts2cardviewer.parser.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

class Sts2CardViewerPanel(private val project: Project) {

    private val LOG = Logger.getInstance(Sts2CardViewerPanel::class.java)

    val mainPanel: JPanel

    private val cardGrid = CardGridPanel()
    private val relicList = RelicListPanel()
    private val detailPanel = CardDetailPanel(project)
    private val tabbedPane = JTabbedPane()
    private val filterToolbar = FilterToolbar()
    private val languageCombo = LanguageComboBox()

    private var allCards = listOf<CardData>()
    private var allRelics = listOf<RelicData>()
    private var projectPath: String? = null
    private var modInfo: ModInfo? = null
    private var codeDirs: List<java.io.File> = emptyList()
    private var fileListenerRegistered = false

    init {
        mainPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 0, 0, 0)

            val toolbarPanel = JPanel(BorderLayout()).apply {
                border = EmptyBorder(4, 8, 4, 8)
                background = UIManager.getColor("Panel.background")
                add(filterToolbar.mainPanel, BorderLayout.CENTER)
                val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                    background = UIManager.getColor("Panel.background")
                    add(JButton("Reload").apply {
                        font = Font("Dialog", Font.PLAIN, 11)
                        addActionListener { loadData() }
                    })
                    add(languageCombo.comboBox)
                }
                add(rightPanel, BorderLayout.EAST)
            }

            tabbedPane.apply {
                addTab("Cards", cardGrid)
                addTab("Relics", relicList)
            }

            val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedPane, detailPanel.mainPanel).apply {
                dividerLocation = 500
                dividerSize = 6
            }

            add(toolbarPanel, BorderLayout.NORTH)
            add(splitPane, BorderLayout.CENTER)

            SwingUtilities.invokeLater { loadData() }
        }

        cardGrid.onCardSelected = { card: CardData -> detailPanel.showCard(card, modInfo) }
        relicList.onRelicSelected = { relic: RelicData -> detailPanel.showRelic(relic, modInfo) }
        filterToolbar.onFilterChanged = { text: String, type: String, rarity: String, target: String, keyword: String ->
            applyFilters(text, type, rarity, target, keyword)
        }
        languageCombo.onLanguageChanged = { langCode: String -> switchLanguage(langCode) }

        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    handleEditorFile(file)
                }
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    event.newFile?.let { handleEditorFile(it) }
                }
            }
        )
    }

    private fun loadData() {
        try {
            LOG.warn("[STS2CardViewer] loadData() called, project.basePath=${project.basePath}")
            projectPath = findProjectPath()
            LOG.warn("[STS2CardViewer] projectPath=$projectPath")
            if (projectPath == null) {
                LOG.warn("[STS2CardViewer] projectPath is null, aborting")
                return
            }

            val info = ModDiscoverer.findModForProject(projectPath!!)
            LOG.warn("[STS2CardViewer] modInfo=$info")
            if (info == null) {
                LOG.warn("[STS2CardViewer] No mod found, aborting")
                return
            }
            modInfo = info

            codeDirs = mutableListOf<java.io.File>().apply {
                java.io.File(info.modRootPath).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                    if (dir.name.endsWith("Code") || dir.name.endsWith("Src")) add(dir)
                }
                if (isEmpty()) {
                    val possible = java.io.File(info.modRootPath, "${info.modId}Code")
                    if (possible.exists()) add(possible)
                }
            }

            if (!fileListenerRegistered) {
                fileListenerRegistered = true
                VirtualFileManager.getInstance().addAsyncFileListener(object : AsyncFileListener {
                    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
                        val changed = events.filterIsInstance<VFileContentChangeEvent>()
                            .filter { e -> e.file.extension == "cs" }
                            .filter { e ->
                                val parent = e.file.parent?.path ?: return@filter false
                                codeDirs.any { dir -> parent.startsWith(dir.absolutePath.replace('\\', '/')) }
                            }.map { it.file.path }
                        if (changed.isEmpty()) return null
                        return object : AsyncFileListener.ChangeApplier {
                            override fun afterVfsChange() {
                                handleFileChanges(changed)
                            }
                        }
                    }
                }, project)
            }

        val language = languageCombo.getCurrentLanguageCode() ?: "eng"
        val localization = LocalizationReader.readLocalization(info.modRootPath, info.modId, language)

        val rawCards = CardMetadataExtractor.extractFromDirectory(info.modRootPath, info.modId)
        LOG.warn("[STS2CardViewer] Extracted ${rawCards.size} cards from ${info.modRootPath}")
        allCards = rawCards.map { card ->
            val snakeName = CardMetadataExtractor.toSnakeCase(card.className)
            val title = localization.titles[snakeName] ?: card.className
            val desc = localization.descriptions[snakeName] ?: ""
            CardData(
                className = card.className,
                title = title,
                description = desc,
                type = card.type,
                rarity = card.rarity,
                target = card.target,
                cost = card.cost,
                damage = card.damage,
                block = card.block,
                keywords = card.keywords,
                portraitPath = card.portraitPath,
                filePath = card.filePath
            )
        }

        val rawRelics = RelicMetadataExtractor.extractFromDirectory(info.modRootPath, info.modId)
        LOG.warn("[STS2CardViewer] Extracted ${rawRelics.size} relics")
        allRelics = rawRelics.map { relic ->
            val snakeName = CardMetadataExtractor.toSnakeCase(relic.className)
            val title = localization.titles[snakeName] ?: relic.className
            val desc = localization.descriptions[snakeName] ?: ""
            val flavor = localization.flavors[snakeName] ?: ""
            RelicData(
                className = relic.className,
                title = title,
                description = desc,
                flavor = flavor,
                rarity = "Common",
                iconPath = relic.iconPath,
                filePath = relic.filePath
            )
        }

        val languages = LocalizationReader.getAvailableLanguages(info.modRootPath, info.modId)
        val langList = if (languages.any { it.code == "eng" }) languages
        else listOf(LanguageInfo("eng", "English")) + languages

        SwingUtilities.invokeLater {
            LOG.warn("[STS2CardViewer] Setting UI: ${allCards.size} cards, ${allRelics.size} relics, ${langList.size} languages")
            languageCombo.setLanguages(langList)
            val allKeywords = allCards.flatMap { it.keywords }.distinct().sorted()
            val allTargets = allCards.mapNotNull { it.target }.distinct().sorted()
            filterToolbar.setKeywords(allKeywords)
            filterToolbar.setTargets(allTargets)
            cardGrid.setCards(allCards, modInfo)
            relicList.setRelics(allRelics, modInfo)
        }
        } catch (e: Exception) {
            LOG.error("[STS2CardViewer] Exception in loadData", e)
        }
    }

    private fun switchLanguage(langCode: String) {
        val info = modInfo ?: return
        val localization = LocalizationReader.readLocalization(info.modRootPath, info.modId, langCode)

        allCards = allCards.map { card ->
            val snakeName = CardMetadataExtractor.toSnakeCase(card.className)
            card.copy(
                title = localization.titles[snakeName] ?: card.className,
                description = localization.descriptions[snakeName] ?: ""
            )
        }

        allRelics = allRelics.map { relic ->
            val snakeName = CardMetadataExtractor.toSnakeCase(relic.className)
            relic.copy(
                title = localization.titles[snakeName] ?: relic.className,
                description = localization.descriptions[snakeName] ?: "",
                flavor = localization.flavors[snakeName] ?: ""
            )
        }

        SwingUtilities.invokeLater {
            cardGrid.setCards(allCards, modInfo)
            relicList.setRelics(allRelics, modInfo)
            detailPanel.clear()
        }
    }

    private fun applyFilters(searchText: String, typeFilter: String, rarityFilter: String, targetFilter: String, keywordFilter: String) {
        val filtered = allCards.filter { card ->
            val matchesSearch = searchText.isEmpty() ||
                    card.title.contains(searchText, ignoreCase = true) ||
                    card.className.contains(searchText, ignoreCase = true) ||
                    card.description.contains(searchText, ignoreCase = true)
            val matchesType = typeFilter == "All" || card.type.equals(typeFilter, ignoreCase = true)
            val matchesRarity = rarityFilter == "All" || card.rarity.equals(rarityFilter, ignoreCase = true)
            val matchesTarget = targetFilter == "All" || card.target.equals(targetFilter, ignoreCase = true)
            val matchesKeyword = keywordFilter == "All" || card.keywords.any { it.equals(keywordFilter, ignoreCase = true) }
            matchesSearch && matchesType && matchesRarity && matchesTarget && matchesKeyword
        }
        cardGrid.setCards(filtered, modInfo)
    }

    private fun handleFileChanges(changedPaths: List<String>) {
        val info = modInfo ?: return
        val language = languageCombo.getCurrentLanguageCode() ?: "eng"
        val localization = LocalizationReader.readLocalization(info.modRootPath, info.modId, language)

        var cardsChanged = false
        var relicsChanged = false
        val currentCardFile = detailPanel.currentCardFile()?.replace('\\', '/')
        val currentRelicFile = detailPanel.currentRelicFile()?.replace('\\', '/')

        for (path in changedPaths) {
            val normalizedPath = path.replace('\\', '/')
            val cardMeta = CardMetadataExtractor.extractFromFile(normalizedPath, info.modId)
            if (cardMeta?.isCard == true) {
                val snakeName = CardMetadataExtractor.toSnakeCase(cardMeta.className)
                val title = localization.titles[snakeName] ?: cardMeta.className
                val desc = localization.descriptions[snakeName] ?: ""
                val updated = CardData(
                    className = cardMeta.className,
                    title = title,
                    description = desc,
                    type = cardMeta.type,
                    rarity = cardMeta.rarity,
                    target = cardMeta.target,
                    cost = cardMeta.cost,
                    damage = cardMeta.damage,
                    block = cardMeta.block,
                    keywords = cardMeta.keywords,
                    portraitPath = cardMeta.portraitPath,
                    filePath = cardMeta.filePath
                )
                val idx = allCards.indexOfFirst { it.filePath?.replace('\\', '/') == normalizedPath }
                if (idx >= 0) {
                    allCards = allCards.toMutableList().apply { set(idx, updated) }
                } else {
                    allCards = allCards + updated
                }
                cardsChanged = true
            }

            val relicMeta = RelicMetadataExtractor.extractFromFile(normalizedPath, info.modId)
            if (relicMeta != null) {
                val snakeName = CardMetadataExtractor.toSnakeCase(relicMeta.className)
                val title = localization.titles[snakeName] ?: relicMeta.className
                val desc = localization.descriptions[snakeName] ?: ""
                val flavor = localization.flavors[snakeName] ?: ""
                val updated = RelicData(
                    className = relicMeta.className,
                    title = title,
                    description = desc,
                    flavor = flavor,
                    rarity = "Common",
                    iconPath = relicMeta.iconPath,
                    filePath = relicMeta.filePath
                )
                val idx = allRelics.indexOfFirst { it.filePath?.replace('\\', '/') == normalizedPath }
                if (idx >= 0) {
                    allRelics = allRelics.toMutableList().apply { set(idx, updated) }
                } else {
                    allRelics = allRelics + updated
                }
                relicsChanged = true
            }
        }

        if (!cardsChanged && !relicsChanged) return

        SwingUtilities.invokeLater {
            if (cardsChanged) {
                val allKeywords = allCards.flatMap { it.keywords }.distinct().sorted()
                val allTargets = allCards.mapNotNull { it.target }.distinct().sorted()
                filterToolbar.setKeywords(allKeywords)
                filterToolbar.setTargets(allTargets)
                cardGrid.setCards(allCards, modInfo)
            }
            if (relicsChanged) relicList.setRelics(allRelics, modInfo)
            if (currentCardFile != null && changedPaths.any { it.replace('\\', '/') == currentCardFile }) {
                val card = allCards.find { it.filePath?.replace('\\', '/') == currentCardFile }
                if (card != null) detailPanel.showCard(card, modInfo)
            }
            if (currentRelicFile != null && changedPaths.any { it.replace('\\', '/') == currentRelicFile }) {
                val relic = allRelics.find { it.filePath?.replace('\\', '/') == currentRelicFile }
                if (relic != null) detailPanel.showRelic(relic, modInfo)
            }
        }
    }

    private fun findProjectPath(): String? {
        val basePath = project.basePath ?: return null
        val propsFile = java.io.File(basePath, "Sts2PathDiscovery.props")
        if (propsFile.exists()) return basePath

        val parentFile = java.io.File(basePath).parentFile
        if (parentFile != null) {
            val parentProps = java.io.File(parentFile.absolutePath, "Sts2PathDiscovery.props")
            if (parentProps.exists()) return parentFile.absolutePath
        }

        return basePath
    }

    private fun handleEditorFile(file: VirtualFile) {
        if (file.extension != "cs") return
        val info = modInfo ?: return
        val path = file.path.replace('\\', '/')

        val card = allCards.find { it.filePath?.replace('\\', '/') == path }
        if (card != null) {
            SwingUtilities.invokeLater {
                tabbedPane.selectedIndex = 0
                detailPanel.showCard(card, info)
            }
            return
        }

        val relic = allRelics.find { it.filePath?.replace('\\', '/') == path }
        if (relic != null) {
            SwingUtilities.invokeLater {
                tabbedPane.selectedIndex = 1
                detailPanel.showRelic(relic, info)
            }
        }
    }
}
