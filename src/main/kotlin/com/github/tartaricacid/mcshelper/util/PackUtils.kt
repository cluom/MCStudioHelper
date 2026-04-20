package com.github.tartaricacid.mcshelper.util

import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.*


class PackUtils {
    companion object {
        private val logger = Logger.getInstance(PackUtils::class.java)

        // manifest.json 是新版本命名，pack_manifest.json 是老版本命名，两者等价
        private val MANIFEST_NAMES = listOf("manifest.json", "pack_manifest.json")

        /**
         * 在目录下查找 manifest 文件；优先使用 manifest.json，其次兼容 pack_manifest.json
         */
        fun findManifest(dir: Path): Path? {
            for (name in MANIFEST_NAMES) {
                val p = dir.resolve(name)
                if (Files.isRegularFile(p)) return p
            }
            return null
        }

        /**
         * 为右键"扫描并链接"使用的扫描：
         * - 若当前目录本身含 manifest，只返回当前目录对应的一个包
         * - 否则扫描子目录（深度 1），对每个含 manifest 的子目录收集
         */
        fun scanForLinking(dir: Path): List<PackInfo> {
            findManifest(dir)?.let { manifest ->
                val info = parseManifest(manifest)
                return if (info != null) listOf(info) else emptyList()
            }
            val result = mutableListOf<PackInfo>()
            try {
                Files.list(dir).use { stream ->
                    stream.forEach { sub ->
                        if (!Files.isDirectory(sub)) return@forEach
                        val manifest = findManifest(sub) ?: return@forEach
                        val info = parseManifest(manifest)
                        if (info != null) result.add(info)
                    }
                }
            } catch (e: Exception) {
                logger.warn("scanForLinking: 扫描 $dir 失败：${e.message}")
            }
            return result
        }

        /**
         * 解析指定路径下的资源包和行为包，找到 manifest 文件并提取相关信息，存储在 packMap 中
         *
         * 如果找到至少一个有效的包，返回 true；否则返回 false
         */
        fun parsePack(path: Path, packMap: EnumMap<PackType, MutableList<PackInfo>>): Boolean {
            var foundPack = false

            // 遍历当前目录下的子目录，检查是否存在 manifest 文件
            Files.walk(path, 2).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach { dir ->
                    val manifestPath = findManifest(dir) ?: return@forEach
                    val packInfo = parseManifest(manifestPath)
                    if (packInfo != null) {
                        val list = packMap.getOrPut(packInfo.type) { mutableListOf() }
                        list.add(packInfo)
                        foundPack = true
                    }
                }
            }

            return foundPack
        }

        /**
         * 扫描给定路径下的所有子目录（深度 1）
         * 返回去重后的 PackInfo 列表，每个 PackInfo 的 path 字段为真实源目录（跟随符号链接）
         */
        fun scanPacksInDirs(vararg dirs: Path?): List<PackInfo> {
            val result = mutableListOf<PackInfo>()
            val seenSources = mutableSetOf<Path>()
            for (dir in dirs) {
                if (dir == null || !Files.isDirectory(dir)) {
                    logger.info("scanPacksInDirs: 跳过目录 $dir (null 或非目录)")
                    continue
                }
                val subs = try {
                    Files.list(dir).use { it.toList() }
                } catch (e: Exception) {
                    logger.warn("scanPacksInDirs: 列出 $dir 失败：${e.message}")
                    continue
                }
                for (sub in subs) {
                    try {
                        if (!Files.isDirectory(sub)) continue
                        val manifestPath = findManifest(sub) ?: continue
                        val realDir = try {
                            sub.toRealPath()
                        } catch (_: Exception) {
                            sub
                        }
                        if (!seenSources.add(realDir)) continue
                        val packInfo = parseManifest(manifestPath, realDir)
                        if (packInfo != null) {
                            result.add(packInfo)
                        } else {
                            logger.info("scanPacksInDirs: $sub 的 manifest 不是合法的 BP/RP")
                        }
                    } catch (e: Exception) {
                        logger.warn("scanPacksInDirs: 解析 $sub 失败：${e.message}")
                    }
                }
            }
            logger.info("scanPacksInDirs: 扫描完成，共 ${result.size} 个包：${result.map { it.name }}")
            return result
        }

        fun parseManifest(manifestPath: Path, sourcePath: Path = manifestPath.parent): PackInfo? {
            val manifestContent = Files.readString(manifestPath)
            val jsonObject = JsonParser.parseString(manifestContent).asJsonObject

            val header = jsonObject.getAsJsonObject("header")
            val uuid = header.get("uuid").asString
            val nameElement = header.get("name")
            val name = if (nameElement != null && !nameElement.isJsonNull) nameElement.asString else ""
            val versionArray = header.getAsJsonArray("version")
            val version = versionArray.joinToString(".") { it.asInt.toString() }

            val modules = jsonObject.getAsJsonArray("modules")
            var packType: PackType? = null
            for (moduleElement in modules) {
                val moduleObj = moduleElement.asJsonObject
                val type = moduleObj.get("type").asString
                when (type) {
                    "data" -> packType = PackType.BEHAVIOR
                    "resources" -> packType = PackType.RESOURCE
                }
            }

            return if (packType != null) {
                PackInfo(packType, name, uuid, version, sourcePath)
            } else {
                null
            }
        }
    }

    data class PackInfo(
        val type: PackType,
        val name: String,
        val uuid: String,
        val version: String,
        val path: Path
    )

    enum class PackType {
        BEHAVIOR,
        RESOURCE
    }
}
