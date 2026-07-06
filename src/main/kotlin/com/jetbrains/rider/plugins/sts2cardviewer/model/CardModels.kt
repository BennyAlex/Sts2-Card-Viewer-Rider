package com.jetbrains.rider.plugins.sts2cardviewer.model

import java.awt.Color

data class CardData(
    val className: String,
    val title: String,
    val description: String,
    val type: String,
    val rarity: String,
    val target: String? = null,
    val cost: Int,
    val damage: Int?,
    val block: Int?,
    val keywords: List<String>,
    val portraitPath: String?,
    val filePath: String?
) {
    val typeColor: Color
        get() = when (type.lowercase()) {
            "attack" -> Color(220, 50, 50)
            "skill" -> Color(50, 150, 220)
            "power" -> Color(34, 197, 94)
            else -> Color(150, 150, 150)
        }

    val rarityColor: Color
        get() = when (rarity.lowercase()) {
            "basic" -> Color(255, 255, 255)
            "common" -> Color(255, 255, 255)
            "uncommon" -> Color(255, 230, 66)
            "rare" -> Color(255, 107, 107)
            "ancient" -> Color(192, 132, 252)
            "token" -> Color(255, 255, 255)
            else -> Color(255, 255, 255)
        }

    val typeBadgeColor: Color
        get() = when (type.lowercase()) {
            "attack" -> Color(220, 50, 50, 40)
            "skill" -> Color(50, 150, 220, 40)
            "power" -> Color(34, 197, 94, 40)
            else -> Color(100, 100, 100, 40)
        }
}

data class RelicData(
    val className: String,
    val title: String,
    val description: String,
    val flavor: String,
    val rarity: String,
    val iconPath: String?,
    val filePath: String?
)

data class LanguageInfo(
    val code: String,
    val name: String
)

data class ModInfo(
    val modId: String,
    val modName: String,
    val modRootPath: String,
    val sts2Path: String,
    val modsPath: String,
    val bigEnergyIconPath: String? = null
)

data class CardViewerResponse(
    val modName: String = "",
    val modId: String = "",
    val availableLanguages: List<LanguageInfo> = emptyList(),
    val cards: List<CardData> = emptyList(),
    val relics: List<RelicData> = emptyList()
)
