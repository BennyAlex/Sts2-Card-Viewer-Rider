package com.jetbrains.rider.plugins.sts2cardviewer.parser

import com.jetbrains.rider.plugins.sts2cardviewer.model.RelicData
import java.io.File

object RelicMetadataExtractor {

    private val CLASS_REGEX = Regex("""class\s+(\w+)\s*:\s*(\w+(?:<[^>]+>)?)""")

    private val KNOWN_RELIC_BASE_TYPES = setOf(
        "CustomRelicModel", "RelicModel", "AbstractModel"
    )

    data class RelicMetadata(
        val className: String,
        val iconPath: String? = null,
        val filePath: String? = null,
        val isRelic: Boolean = false,
        val isAbstract: Boolean = false
    )

    fun extractFromDirectory(modRootPath: String, modId: String): List<RelicMetadata> {
        val relics = mutableListOf<RelicMetadata>()

        val codeDirs = mutableListOf<File>()
        File(modRootPath).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (dir.name.endsWith("Code") || dir.name.endsWith("Src")) {
                codeDirs.add(dir)
            }
        }

        if (codeDirs.isEmpty()) {
            val possibleCodeDir = File(modRootPath, "${modId}Code")
            if (possibleCodeDir.exists()) codeDirs.add(possibleCodeDir)
        }

        for (codeDir in codeDirs) {
            val relicsDir = File(codeDir, "Relics")
            if (!relicsDir.exists()) continue

            relicsDir.walkTopDown().filter { it.extension == "cs" }.forEach { csFile ->
                val relic = extractFromFile(csFile.absolutePath, modId)
                if (relic?.isRelic == true && !relic.isAbstract) relics.add(relic)
            }
        }

        return relics
    }

    fun extractFromFile(filePath: String, modId: String): RelicMetadata? {
        return try {
            val normalized = filePath.replace('\\', '/')
            val content = File(normalized).readText()
            extractFromContent(content, normalized, modId)
        } catch (e: Exception) {
            null
        }
    }

    fun extractFromContent(content: String, filePath: String, modId: String): RelicMetadata? {
        val classMatch = CLASS_REGEX.find(content) ?: return null

        val className = classMatch.groupValues[1]
        val baseType = classMatch.groupValues[2]

        val isRelic = KNOWN_RELIC_BASE_TYPES.any { baseType.contains(it, ignoreCase = true) } ||
                      baseType.contains("Relic", ignoreCase = true) ||
                      baseType.contains("RelicModel", ignoreCase = true)

        if (!isRelic) return null

        val isAbstract = content.contains("abstract class")

        val snakeName = CardMetadataExtractor.toSnakeCase(className)
        val iconPath = "$snakeName.png"

        return RelicMetadata(
            className = className,
            iconPath = iconPath,
            filePath = filePath,
            isRelic = true,
            isAbstract = isAbstract
        )
    }
}
