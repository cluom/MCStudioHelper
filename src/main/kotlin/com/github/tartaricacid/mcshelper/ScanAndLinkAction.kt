package com.github.tartaricacid.mcshelper

import com.github.tartaricacid.mcshelper.util.FileUtils
import com.github.tartaricacid.mcshelper.util.PackUtils
import com.github.tartaricacid.mcshelper.util.PackUtils.PackType
import com.github.tartaricacid.mcshelper.util.PathUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

@Suppress("DialogTitleCapitalization")
class ScanAndLinkAction : AnAction() {
    private val logger = Logger.getInstance(ScanAndLinkAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null && file.isDirectory
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vf = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!vf.isDirectory) return
        val dir = vf.toNioPath()

        val packs = PackUtils.scanForLinking(dir)
        if (packs.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "在该目录及其一级子目录下未发现合法的 manifest.json / pack_manifest.json",
                "扫描并链接"
            )
            return
        }

        val bpDir = PathUtils.behaviorPacksDir()
        val rpDir = PathUtils.resourcePacksDir()
        if (bpDir == null || rpDir == null) {
            Messages.showErrorDialog(
                project,
                "未找到启动器的 behavior_packs / resource_packs 目录，请先正常启动过一次游戏",
                "扫描并链接"
            )
            return
        }

        // 在带进度框的同步过程中建链，避免 UAC 弹窗期间 UI 僵死看起来像卡顿
        val result = ProgressManager.getInstance().runProcessWithProgressSynchronously<LinkResult, RuntimeException>(
            { linkPacks(packs, bpDir, rpDir) }, "正在创建符号链接...", false, project
        )

        val msg = buildString {
            if (result.linked.isNotEmpty()) {
                append("已链接 ${result.linked.size} 个组件：\n")
                append(result.linked.joinToString("\n") { "  • $it" })
            }
            if (result.skipped.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("已跳过 ${result.skipped.size} 项：\n")
                append(result.skipped.joinToString("\n") { "  • $it" })
            }
            if (isEmpty()) append("没有需要处理的包")
        }
        Messages.showInfoMessage(project, msg, "扫描并链接")
    }

    private fun linkPacks(packs: List<PackUtils.PackInfo>, bpDir: Path, rpDir: Path): LinkResult {
        val sudoList = mutableSetOf<Pair<Path, Path>>()
        val linked = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (pack in packs) {
            val targetDir = when (pack.type) {
                PackType.BEHAVIOR -> bpDir
                PackType.RESOURCE -> rpDir
            }
            val target = pack.path
            val link = targetDir.resolve(pack.path.fileName.toString())

            // 源就是目标位置（已在 MCS 目录里），无需处理
            if (target.toAbsolutePath().normalize() == link.toAbsolutePath().normalize()) {
                skipped += "${pack.name}（已在启动器目录下）"
                continue
            }

            try {
                if (!FileUtils.createSymlink(target, link)) {
                    sudoList += target to link
                }
                linked += "${pack.name} [${pack.type}]"
            } catch (e: Exception) {
                logger.warn("为 ${pack.path} 建立符号链接失败：${e.message}", e)
                skipped += "${pack.name}（失败：${e.message}）"
            }
        }

        if (sudoList.isNotEmpty()) {
            FileUtils.sudoCreateSymlinks(sudoList.toList())
        }
        return LinkResult(linked, skipped)
    }

    private data class LinkResult(val linked: List<String>, val skipped: List<String>)
}
