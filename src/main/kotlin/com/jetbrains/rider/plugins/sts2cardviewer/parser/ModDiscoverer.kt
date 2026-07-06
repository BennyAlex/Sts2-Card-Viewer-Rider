package com.jetbrains.rider.plugins.sts2cardviewer.parser

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.rider.plugins.sts2cardviewer.model.ModInfo
import java.io.File

object ModDiscoverer {
    private val LOG = Logger.getInstance(ModDiscoverer::class.java)

    fun findSts2Path(projectRootPath: String): String? {
        val defaultPath = findDefaultPath()
        if (defaultPath != null) {
            LOG.warn("[STS2CardViewer] Found STS2 via default path: $defaultPath")
            return defaultPath
        }

        val propsFile = File(projectRootPath, "Sts2PathDiscovery.props")
        if (propsFile.exists()) {
            try {
                val content = propsFile.readText()
                val concretePathPattern = Regex("""[A-Za-z]:[\\/.][^\s<>"]+""")
                val matches = concretePathPattern.findAll(content)
                    .map { it.value.trimEnd('\\', '/') }
                    .filter { it.contains("Slay the Spire 2", ignoreCase = true) && File(it).exists() }
                    .toList()

                if (matches.isNotEmpty()) {
                    return matches.first()
                }
            } catch (e: Exception) {
                LOG.warn("[STS2CardViewer] Failed to parse props file: ${e.message}")
            }
        }

        return null
    }

    private fun findDefaultPath(): String? {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> findWindowsPath()
            osName.contains("linux") -> findLinuxPath()
            osName.contains("mac") -> findMacPath()
            else -> null
        }
    }

    private fun findWindowsPath(): String? {
        val possiblePaths = mutableListOf<String>()

        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "reg", "query",
                "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Steam App 2868840",
                "/v", "InstallLocation"
            ))
            val output = process.inputStream.bufferedReader().readText()
            val match = Regex("""InstallLocation\s+REG_SZ\s+(.+)""").find(output)
            if (match != null) {
                possiblePaths.add(match.groupValues[1].trim())
            }
        } catch (e: Exception) { }

        for (drive in 'C'..'Z') {
            possiblePaths.add("$drive:\\Steam\\steamapps\\common\\Slay the Spire 2")
            possiblePaths.add("$drive:\\SteamLibrary\\steamapps\\common\\Slay the Spire 2")
        }

        return possiblePaths.firstOrNull { File(it).exists() }
    }

    private fun findLinuxPath(): String? {
        val home = System.getProperty("user.home")
        val paths = listOf(
            "$home/.local/share/Steam/steamapps/common/Slay the Spire 2",
            "$home/.steam/steam/steamapps/common/Slay the Spire 2",
            "$home/.steam/steamapps/common/Slay the Spire 2"
        )
        return paths.firstOrNull { File(it).exists() }
    }

    private fun findMacPath(): String? {
        val home = System.getProperty("user.home")
        val paths = listOf(
            "$home/Library/Application Support/Steam/steamapps/common/Slay the Spire 2",
            "$home/Library/Application Support/Steam/steamapps/common/SlayTheSpire2"
        )
        return paths.firstOrNull { File(it).exists() }
    }

    fun findModForProject(projectRootPath: String): ModInfo? {
        val rootDir = File(projectRootPath)
        LOG.warn("[STS2CardViewer] findModForProject: $projectRootPath")

        val jsonFiles = rootDir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".json") && !it.name.startsWith(".")
        } ?: emptyList()
        LOG.warn("[STS2CardViewer] JSON files in project root: ${jsonFiles.map { it.name }}")

        for (manifestFile in jsonFiles) {
            try {
                val json = manifestFile.readText()

                val idPattern = """"id"\s*:\s*"([^"]+)""""
                val idMatch = Regex(idPattern).find(json)

                if (idMatch == null) continue

                val modId = idMatch.groupValues[1]

                val namePattern = """"name"\s*:\s*"([^"]+)""""
                val nameMatch = Regex(namePattern).find(json)
                val modName = nameMatch?.groupValues?.get(1) ?: modId

                val sts2Path = findSts2Path(projectRootPath) ?: ""
                val energyIcon = findBigEnergyIconPath(projectRootPath, modId)

                val info = ModInfo(
                    modId = modId,
                    modName = modName,
                    modRootPath = projectRootPath,
                    sts2Path = sts2Path,
                    modsPath = "",
                    bigEnergyIconPath = energyIcon
                )
                LOG.warn("[STS2CardViewer] Found mod: id=$modId, name=$modName, root=$projectRootPath")
                return info
            } catch (e: Exception) {
                LOG.warn("[STS2CardViewer] Error reading ${manifestFile.name}: ${e.message}")
            }
        }

        LOG.warn("[STS2CardViewer] No mod manifest found in project root")
        return null
    }

    private fun findBigEnergyIconPath(projectRootPath: String, modId: String): String? {
        val codeDirs = mutableListOf<File>()
        File(projectRootPath).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            if (dir.name.endsWith("Code") || dir.name.endsWith("Src")) {
                codeDirs.add(dir)
            }
        }
        if (codeDirs.isEmpty()) {
            val possibleCodeDir = File(projectRootPath, "${modId}Code")
            if (possibleCodeDir.exists()) codeDirs.add(possibleCodeDir)
        }
        LOG.warn("[STS2CardViewer] Searching for BigEnergyIconPath in ${codeDirs.map { it.name }}")

        val poolRegex = Regex("""BigEnergyIconPath\s*=>\s*"([^"]+)"""")

        for (codeDir in codeDirs) {
            codeDir.walkTopDown().filter { it.extension == "cs" }.forEach { csFile ->
                try {
                    val content = csFile.readText()
                    if (content.contains("BigEnergyIconPath")) {
                        LOG.warn("[STS2CardViewer] Found BigEnergyIconPath reference in ${csFile.name}")
                        val match = poolRegex.find(content)
                        if (match != null) {
                            val path = match.groupValues[1].trim('"')
                            LOG.warn("[STS2CardViewer] BigEnergyIconPath = $path")
                            val fullPath = File(projectRootPath, "$modId/images/$path")
                            LOG.warn("[STS2CardViewer] Checking path: ${fullPath.absolutePath}, exists=${fullPath.exists()}")
                            if (fullPath.exists()) {
                                return fullPath.absolutePath
                            }
                        } else {
                            LOG.warn("[STS2CardViewer] Regex did not match in ${csFile.name}")
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("[STS2CardViewer] Error reading ${csFile.name}: ${e.message}")
                }
            }
        }
        LOG.warn("[STS2CardViewer] No BigEnergyIconPath found")
        return null
    }
}
