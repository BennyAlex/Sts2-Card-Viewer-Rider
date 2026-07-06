package com.jetbrains.rider.plugins.sts2cardviewer.parser

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

object AssetPathResolver {

    fun resolveCardPortrait(modRootPath: String, modId: String, portraitFileName: String): String? {
        val candidates = listOf(
            File(modRootPath, "$modId/images/card_portraits/$portraitFileName"),
            File(modRootPath, "images/card_portraits/$portraitFileName"),
            File(modRootPath, "$modId/images/card_portraits/big/$portraitFileName"),
            File(modRootPath, "images/card_portraits/big/$portraitFileName")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }

    fun resolveRelicIcon(modRootPath: String, modId: String, iconFileName: String): String? {
        val candidates = listOf(
            File(modRootPath, "$modId/images/relics/$iconFileName"),
            File(modRootPath, "images/relics/$iconFileName"),
            File(modRootPath, "$modId/images/relics/big/$iconFileName"),
            File(modRootPath, "images/relics/big/$iconFileName")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }

    fun getFallbackCardPortrait(modRootPath: String, modId: String): String? {
        val candidates = listOf(
            File(modRootPath, "$modId/images/card_portraits/card.png"),
            File(modRootPath, "images/card_portraits/card.png")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }

    fun getFallbackRelicIcon(modRootPath: String, modId: String): String? {
        val candidates = listOf(
            File(modRootPath, "$modId/images/relics/relic.png"),
            File(modRootPath, "images/relics/relic.png")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }

    fun imageToBase64(imagePath: String): String? {
        return try {
            val bytes = File(imagePath).readBytes()
            Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            null
        }
    }

    fun getMimeType(imagePath: String): String {
        val ext = File(imagePath).extension.lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/png"
        }
    }

    fun loadImage(imagePath: String): BufferedImage? {
        return try {
            ImageIO.read(File(imagePath))
        } catch (e: Exception) {
            null
        }
    }
}
