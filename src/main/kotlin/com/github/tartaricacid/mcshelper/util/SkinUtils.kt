package com.github.tartaricacid.mcshelper.util

import com.intellij.openapi.application.PathManager
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object SkinUtils {
    const val DEFAULT_SKIN_NAME = "原版 Steve"

    /** 皮肤存放在 IDE 配置目录中，插件升级后仍会保留。 */
    fun skinsDir(): Path = Paths.get(PathManager.getConfigPath(), "mcs-helper", "skins")

    fun listSkinFileNames(): List<String> {
        val dir = skinsDir()
        Files.createDirectories(dir)
        return Files.list(dir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".png", ignoreCase = true) }
                .map { it.fileName.toString() }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
    }

    /** 导入 PNG；同名文件不覆盖，避免误删用户已经保存的皮肤。 */
    fun importSkin(source: Path): String {
        require(Files.isRegularFile(source)) { "皮肤文件不存在" }
        require(source.fileName.toString().endsWith(".png", ignoreCase = true)) { "仅支持 PNG 皮肤文件" }

        val dir = skinsDir()
        Files.createDirectories(dir)
        val target = dir.resolve(source.fileName.toString())
        if (Files.exists(target)) {
            throw FileAlreadyExistsException(target.toString())
        }
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
        return target.fileName.toString()
    }

    /** 只允许解析皮肤目录中的单个文件名，阻止运行配置通过 ../ 越出目录。 */
    fun resolveSkin(fileName: String): Path? {
        if (fileName.isBlank() || Paths.get(fileName).fileName.toString() != fileName) return null
        val dir = skinsDir().toAbsolutePath().normalize()
        val skin = dir.resolve(fileName).normalize()
        return if (
            skin.startsWith(dir) &&
            Files.isRegularFile(skin) &&
            skin.fileName.toString().endsWith(".png", ignoreCase = true)
        ) skin else null
    }
}
