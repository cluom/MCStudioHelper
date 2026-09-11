package com.github.tartaricacid.mcshelper.util

import com.intellij.openapi.application.PathManager
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

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

    /**
     * 将皮肤转换为启动器稳定支持的 64 像素宽 PNG，并写到纯英文运行时文件名。
     * 64×64 / 64×32 原样重编码，更高分辨率的等比例皮肤使用最近邻缩小，避免像素模糊。
     */
    fun prepareSkinForLaunch(source: Path, target: Path): Path {
        val image = ImageIO.read(source.toFile()) ?: throw IllegalArgumentException("无法读取 PNG 皮肤：${source.fileName}")
        val targetHeight = when {
            image.width == image.height -> 64
            image.width == image.height * 2 -> 32
            else -> throw IllegalArgumentException("皮肤尺寸比例必须为 1:1 或 2:1，当前为 ${image.width}×${image.height}")
        }
        require(image.width >= 64 && image.width % 64 == 0) {
            "皮肤宽度必须为 64 的整数倍，当前为 ${image.width}"
        }

        val normalized = BufferedImage(64, targetHeight, BufferedImage.TYPE_INT_ARGB)
        normalized.createGraphics().use { graphics ->
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            graphics.drawImage(image, 0, 0, 64, targetHeight, null)
        }
        Files.createDirectories(target.parent)
        check(ImageIO.write(normalized, "png", target.toFile())) { "无法写入运行时皮肤 PNG" }
        return target
    }

    private inline fun <T : java.awt.Graphics> T.use(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
