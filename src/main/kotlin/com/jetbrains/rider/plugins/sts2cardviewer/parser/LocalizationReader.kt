package com.jetbrains.rider.plugins.sts2cardviewer.parser

import com.jetbrains.rider.plugins.sts2cardviewer.model.LanguageInfo
import java.io.File

object LocalizationReader {

    private val LANGUAGE_DISPLAY_NAMES = mapOf(
        "eng" to "English",
        "deu" to "Deutsch",
        "esp" to "Espa\u00f1ol",
        "fra" to "Fran\u00e7ais",
        "ita" to "Italiano",
        "jpn" to "\u65e5\u672c\u8a9e",
        "kor" to "\ud55c\uad6d\uc5b4",
        "pol" to "Polski",
        "ptb" to "Portugu\u00eas (BR)",
        "rus" to "\u0420\u0443\u0441\u0441\u043a\u0438\u0439",
        "spa" to "Espa\u00f1ol",
        "tha" to "\u0e44\u0e17\u0e22",
        "tur" to "T\u00fcrk\u00e7e",
        "zhs" to "\u4e2d\u6587(\u7b80\u4f53)"
    )

    data class LocalizationData(
        val titles: MutableMap<String, String> = mutableMapOf(),
        val descriptions: MutableMap<String, String> = mutableMapOf(),
        val flavors: MutableMap<String, String> = mutableMapOf()
    )

    fun getAvailableLanguages(modRootPath: String, modId: String): List<LanguageInfo> {
        val languages = mutableListOf<LanguageInfo>()

        val locDirs = listOf(
            File(modRootPath, "$modId/localization"),
            File(modRootPath, "localization")
        )

        for (locDir in locDirs) {
            if (!locDir.exists()) continue

            locDir.listFiles()?.filter { it.isDirectory }?.forEach { langDir ->
                val langCode = langDir.name
                val displayName = LANGUAGE_DISPLAY_NAMES[langCode] ?: langCode.uppercase()
                if (languages.none { it.code == langCode }) {
                    languages.add(LanguageInfo(langCode, displayName))
                }
            }
        }

        val engIndex = languages.indexOfFirst { it.code == "eng" }
        if (engIndex > 0) {
            val eng = languages.removeAt(engIndex)
            languages.add(0, eng)
        }

        return languages
    }

    fun readLocalization(modRootPath: String, modId: String, language: String): LocalizationData {
        val locDir = findLocalizationDir(modRootPath, modId, language) ?: return LocalizationData()

        val titles = mutableMapOf<String, String>()
        val descriptions = mutableMapOf<String, String>()
        val flavors = mutableMapOf<String, String>()

        val cardsFile = File(locDir, "cards.json")
        if (cardsFile.exists()) readCardLocalization(cardsFile, modId, titles, descriptions)

        val relicsFile = File(locDir, "relics.json")
        if (relicsFile.exists()) readRelicLocalization(relicsFile, modId, titles, descriptions, flavors)

        return LocalizationData(titles, descriptions, flavors)
    }

    private fun findLocalizationDir(modRootPath: String, modId: String, language: String): File? {
        val candidates = listOf(
            File(modRootPath, "$modId/localization/$language"),
            File(modRootPath, "localization/$language"),
            File(modRootPath, "$modId/localization/eng"),
            File(modRootPath, "localization/eng")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }

    private fun readCardLocalization(file: File, modId: String, titles: MutableMap<String, String>, descriptions: MutableMap<String, String>) {
        try {
            val json = file.readText()
            val prefix = "${modId.uppercase()}-"

            Regex(""""${Regex.escape(prefix)}(.+?)\.title"\s*:\s*"([^"]*)"""").findAll(json).forEach { match ->
                titles[match.groupValues[1].lowercase()] = match.groupValues[2]
            }
            Regex(""""${Regex.escape(prefix)}(.+?)\.description"\s*:\s*"([^"]*)"""").findAll(json).forEach { match ->
                descriptions[match.groupValues[1].lowercase()] = match.groupValues[2].replace("\\n", "\n")
            }
        } catch (e: Exception) { }
    }

    private fun readRelicLocalization(file: File, modId: String, titles: MutableMap<String, String>, descriptions: MutableMap<String, String>, flavors: MutableMap<String, String>) {
        try {
            val json = file.readText()
            val prefix = "${modId.uppercase()}-"

            Regex(""""${Regex.escape(prefix)}(.+?)\.title"\s*:\s*"([^"]*)"""").findAll(json).forEach { match ->
                titles[match.groupValues[1].lowercase()] = match.groupValues[2]
            }
            Regex(""""${Regex.escape(prefix)}(.+?)\.description"\s*:\s*"([^"]*)"""").findAll(json).forEach { match ->
                descriptions[match.groupValues[1].lowercase()] = match.groupValues[2].replace("\\n", "\n")
            }
            Regex(""""${Regex.escape(prefix)}(.+?)\.flavor"\s*:\s*"([^"]*)"""").findAll(json).forEach { match ->
                flavors[match.groupValues[1].lowercase()] = match.groupValues[2]
            }
        } catch (e: Exception) { }
    }

    fun cleanDescription(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return text
            .replace("\\n", "\n")
            .replace(Regex("\\{[^}]*:.*?\\}"), "[*]")
            .replace(Regex("\\[\\w+\\]"), "")
            .replace(Regex("\\[/\\w+\\]"), "")
            .replace("\n", "<br>")
            .trim()
    }
}
