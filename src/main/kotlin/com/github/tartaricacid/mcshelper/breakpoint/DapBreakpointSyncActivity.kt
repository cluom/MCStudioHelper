package com.github.tartaricacid.mcshelper.breakpoint

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

/**
 * Python 行断点与 LSP4IJ 的 DAP 行断点镜像同步。
 * - 打 Python 行断点 → 自动在同位置创建 DAP 断点（等同于勾选）
 * - 删 Python 行断点 → 自动删掉同位置的 DAP 断点
 * - 启用/禁用 Python 行断点 → 同步 DAP 断点的 isEnabled
 *
 * DAP 断点类型由 LSP4IJ 0.19.0 提供：typeId = "dap-breakpoint"，
 * 对应 com.redhat.devtools.lsp4ij.dap.breakpoints.DAPBreakpointType
 */
class DapBreakpointSyncActivity : ProjectActivity {
    companion object {
        private val logger = Logger.getInstance(DapBreakpointSyncActivity::class.java)
        private const val DAP_TYPE_ID = "dap-breakpoint"

        // 同步期间抑制自身再次触发 listener，防止递归
        @Volatile
        private var syncing = false
    }

    override suspend fun execute(project: Project) {
        val connection = project.messageBus.connect()
        connection.subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
            override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
                if (syncing || !isPythonLineBreakpoint(breakpoint)) return
                deferSync(project) { syncAddDap(project, breakpoint as XLineBreakpoint<*>) }
            }

            override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
                if (syncing || !isPythonLineBreakpoint(breakpoint)) return
                deferSync(project) { syncRemoveDap(project, breakpoint as XLineBreakpoint<*>) }
            }

            override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
                if (syncing || !isPythonLineBreakpoint(breakpoint)) return
                deferSync(project) { syncDapEnabled(project, breakpoint as XLineBreakpoint<*>) }
            }
        })
    }

    /** 延迟到下一个事件循环执行，避免在断点事件派发链内直接拿 write action 造成死锁 */
    private fun deferSync(project: Project, block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(
            {
                if (project.isDisposed) return@invokeLater
                syncing = true
                try {
                    WriteAction.run<RuntimeException> { block() }
                } catch (e: Throwable) {
                    logger.warn("DAP 断点同步失败：${e.message}", e)
                } finally {
                    syncing = false
                }
            },
            { project.isDisposed }
        )
    }

    private fun isPythonLineBreakpoint(bp: XBreakpoint<*>): Boolean {
        if (bp !is XLineBreakpoint<*>) return false
        val id = bp.type.id
        return id == "python-line" || (id.contains("python", ignoreCase = true) && !id.contains("exception", ignoreCase = true))
    }

    @Suppress("UNCHECKED_CAST")
    private fun getDapType(): XLineBreakpointType<XBreakpointProperties<*>>? {
        val type = XBreakpointType.EXTENSION_POINT_NAME.extensionList.firstOrNull { it.id == DAP_TYPE_ID }
        return type as? XLineBreakpointType<XBreakpointProperties<*>>
    }

    private fun findDapBreakpointAt(
        project: Project,
        dapType: XLineBreakpointType<XBreakpointProperties<*>>,
        fileUrl: String,
        line: Int
    ): XLineBreakpoint<XBreakpointProperties<*>>? {
        val manager = XDebuggerManager.getInstance(project).breakpointManager
        return manager.getBreakpoints(dapType).firstOrNull { it.fileUrl == fileUrl && it.line == line }
    }

    private fun syncAddDap(project: Project, pyBp: XLineBreakpoint<*>) {
        val dapType = getDapType() ?: run {
            logger.info("未找到 DAP 断点类型 (id=$DAP_TYPE_ID)")
            return
        }
        val url = pyBp.fileUrl
        val line = pyBp.line
        if (findDapBreakpointAt(project, dapType, url, line) != null) return

        val vf = VirtualFileManager.getInstance().findFileByUrl(url) ?: run {
            logger.info("无法把 URL 解析为 VirtualFile: $url")
            return
        }
        // 预检：若没有支持此文件的 Debug Adapter，LSP4IJ 不允许在该行打 DAP 断点
        if (!dapType.canPutAt(vf, line, project)) {
            logger.info("DAP 不支持此文件/行的断点: $url:$line")
            return
        }
        val props: XBreakpointProperties<*>? = try {
            dapType.createBreakpointProperties(vf, line)
        } catch (_: Throwable) {
            null
        }
        XDebuggerManager.getInstance(project).breakpointManager.addLineBreakpoint(dapType, url, line, props)
    }

    private fun syncRemoveDap(project: Project, pyBp: XLineBreakpoint<*>) {
        val dapType = getDapType() ?: return
        val existing = findDapBreakpointAt(project, dapType, pyBp.fileUrl, pyBp.line) ?: return
        XDebuggerManager.getInstance(project).breakpointManager.removeBreakpoint(existing)
    }

    private fun syncDapEnabled(project: Project, pyBp: XLineBreakpoint<*>) {
        val dapType = getDapType() ?: return
        val dapBp = findDapBreakpointAt(project, dapType, pyBp.fileUrl, pyBp.line) ?: return
        if (dapBp.isEnabled != pyBp.isEnabled) {
            dapBp.isEnabled = pyBp.isEnabled
        }
    }
}
