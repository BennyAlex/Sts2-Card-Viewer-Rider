package com.jetbrains.rider.plugins.sts2cardviewer.parser

import com.jetbrains.rider.plugins.sts2cardviewer.model.CardData
import java.io.File

object CardMetadataExtractor {

    private val CLASS_REGEX = Regex("""class\s+(\w+)\s*:\s*(\w+(?:<[^>]+>)?)""")
    private val CONSTRUCTOR_REGEX = Regex(
        """base\s*\(\s*(\d+)\s*,\s*(\w+)\s*\.\s*(\w+)\s*,\s*(\w+)\s*\.\s*(\w+)\s*,\s*(\w+)\s*\.\s*(\w+)"""
    )
    private val DAMAGE_REGEX = Regex("""WithDamage\s*\(\s*(\d+)(?:\s*,\s*(\d+))?\s*\)""")
    private val BLOCK_REGEX = Regex("""WithBlock\s*\(\s*(\d+)(?:\s*,\s*(\d+))?\s*\)""")
    private val KEYWORD_REGEX = Regex("""CardKeyword\.(\w+)""")
    private val POOL_REGEX = Regex("""\[Pool\s*\(\s*typeof\s*\(\s*(\w+)\s*\)\s*\)\s*\]""")

    private val KNOWN_CARD_BASE_TYPES = setOf(
        "ConstructedCardModel", "CustomCardModel", "CardModel", "ToonLinkCard"
    )

    data class CardMetadata(
        val className: String,
        val type: String = "Attack",
        val rarity: String = "Common",
        val target: String? = null,
        val cost: Int = 0,
        val damage: Int? = null,
        val block: Int? = null,
        val keywords: List<String> = emptyList(),
        val portraitPath: String? = null,
        val filePath: String? = null,
        val isCard: Boolean = false
    )

    fun extractFromDirectory(modRootPath: String, modId: String): List<CardMetadata> {
        val cards = mutableListOf<CardMetadata>()

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
            codeDir.walkTopDown().filter { it.extension == "cs" }.forEach { csFile ->
                val card = extractFromFile(csFile.absolutePath, modId)
                if (card?.isCard == true) cards.add(card)
            }
        }

        return cards
    }

    fun extractFromFile(filePath: String, modId: String): CardMetadata? {
        return try {
            val normalized = filePath.replace('\\', '/')
            val content = File(normalized).readText()
            extractFromContent(content, normalized, modId)
        } catch (e: Exception) {
            null
        }
    }

    fun extractFromContent(content: String, filePath: String, modId: String): CardMetadata? {
        val classMatch = CLASS_REGEX.find(content) ?: return null

        val className = classMatch.groupValues[1]
        val baseType = classMatch.groupValues[2]

        var isCard = false
        var cost = 0
        var type = "Attack"
        var rarity = "Common"
        var target: String? = null

        val constructorMatch = CONSTRUCTOR_REGEX.find(content)
        if (constructorMatch != null) {
            isCard = true
            cost = constructorMatch.groupValues[1].toIntOrNull() ?: 0
            type = constructorMatch.groupValues[3]
            rarity = constructorMatch.groupValues[5]
            val targetGroup = constructorMatch.groupValues.getOrNull(7)
            if (!targetGroup.isNullOrBlank()) target = targetGroup
        } else {
            isCard = KNOWN_CARD_BASE_TYPES.any { baseType == it || baseType.endsWith(it) }
        }

        if (!isCard) return null

        val damage = DAMAGE_REGEX.find(content)?.groupValues?.get(1)?.toIntOrNull()
        val block = BLOCK_REGEX.find(content)?.groupValues?.get(1)?.toIntOrNull()

        val keywords = mutableListOf<String>()
        KEYWORD_REGEX.findAll(content).forEach { match ->
            val kw = match.groupValues[1]
            if (kw !in keywords) keywords.add(kw)
        }
        if (content.contains("CardKeyword.Exhaust") && "Exhaust" !in keywords) {
            keywords.add("Exhaust")
        }

        val snakeName = toSnakeCase(className)
        val portraitPath = "$snakeName.png"

        return CardMetadata(
            className = className,
            type = type,
            rarity = rarity,
            target = target,
            cost = cost,
            damage = damage,
            block = block,
            keywords = keywords,
            portraitPath = portraitPath,
            filePath = filePath,
            isCard = true
        )
    }

    fun toSnakeCase(name: String): String {
        return buildString {
            name.forEachIndexed { i, c ->
                if (i > 0 && c.isUpperCase()) {
                    append('_')
                    append(c.lowercaseChar())
                } else {
                    append(c.lowercaseChar())
                }
            }
        }
    }
}
