package com.github.tartaricacid.mcshelper.gui

import com.github.tartaricacid.mcshelper.options.GameMode
import com.github.tartaricacid.mcshelper.options.LevelType
import com.github.tartaricacid.mcshelper.options.LogLevel
import com.github.tartaricacid.mcshelper.options.PlayerPermissionLevel
import com.github.tartaricacid.mcshelper.run.MCRunConfiguration
import com.github.tartaricacid.mcshelper.util.FileUtils
import com.github.tartaricacid.mcshelper.util.PackUtils
import com.github.tartaricacid.mcshelper.util.PathUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CheckBoxList
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.textFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Desktop
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.random.Random

@Suppress("DialogTitleCapitalization")
class MCSettingsEditor : SettingsEditor<MCRunConfiguration>() {
    private val logger = Logger.getInstance(MCSettingsEditor::class.java)
    private val runConfig: JComponent

    private lateinit var gameExeField: TextFieldWithHistoryWithBrowseButton

    private lateinit var logLevel: ComboBox<LogLevel>

    private lateinit var projectPackList: CheckBoxList<String>
    private lateinit var externalPackList: CheckBoxList<String>

    private lateinit var worldFolderField: JBTextField
    private lateinit var worldFolderLink: ActionLink
    private lateinit var worldFolderDeleteBtn: JButton
    private lateinit var worldFolderPanel: JPanel
    private lateinit var worldFolderCards: CardLayout
    private var worldFolderEditable: Boolean = true

    companion object {
        private const val WORLD_CARD_EDIT = "edit"
        private const val WORLD_CARD_VIEW = "view"

        private val DEFAULT_FLAT_LAYERS = listOf(
            "minecraft:bedrock=1",
            "minecraft:dirt=2",
            "minecraft:grass=1"
        )

        /** 解析 "blockName=count"，返回 (blockName, count) 或 null */
        fun parseLayerEntry(entry: String): Pair<String, Int>? {
            val parts = entry.split("=", limit = 2)
            if (parts.size != 2) return null
            val name = parts[0].trim()
            val count = parts[1].trim().toIntOrNull() ?: return null
            if (name.isEmpty() || count <= 0) return null
            return name to count
        }
    }

    private lateinit var worldSeedField: JBTextField
    private lateinit var userNameField: JBTextField

    private lateinit var gameModeField: ComboBox<GameMode>
    private lateinit var levelTypeField: ComboBox<LevelType>
    private lateinit var playerPermissionsLevelField: ComboBox<PlayerPermissionLevel>
    private lateinit var showCoordinatesField: JBCheckBox

    private lateinit var flatLayersPreviewTable: com.intellij.ui.table.JBTable
    private lateinit var flatLayersPreviewModel: javax.swing.table.DefaultTableModel
    private lateinit var flatLayersLabelRow: com.intellij.ui.dsl.builder.Row
    private lateinit var flatLayersTableRow: com.intellij.ui.dsl.builder.Row
    // NBT 顺序存（自底向上），每项 "blockName=count"
    private var flatLayersCurrent: MutableList<String> = mutableListOf()

    private lateinit var enableCheatsField: JBCheckBox
    private lateinit var keepInventoryField: JBCheckBox
    private lateinit var doDaylightCycleField: JBCheckBox
    private lateinit var doWeatherCycleField: JBCheckBox

    init {
        runConfig = panel {
            row("启动程序路径：") {
                val fileChooser = FileChooserDescriptorFactory.singleFile()
                    .withTitle("启动程序路径")
                    .withDescription("请选择网易开发者游戏启动器所在路径")
                    .withExtensionFilter("exe")
                gameExeField = textFieldWithHistoryWithBrowseButton(
                    null, fileChooser, FileUtils.Companion::findMinecraftExecutables
                )
                cell(gameExeField).comment("开发者游戏启动器所在路径").align(Align.FILL)
            }

            row("日志等级：") {
                logLevel = comboBox(
                    EnumComboBoxModel(LogLevel::class.java),
                    textListCellRenderer { it?.displayName }
                ).comment("控制 PyCharm 输出日志的详细程度").component
            }

            row("项目内组件：") {
                projectPackList = CheckBoxList()
                projectPackList.emptyText.text = "未在当前项目下发现合法的资源包或行为包"
                val scroll = JBScrollPane(projectPackList)
                scroll.preferredSize = java.awt.Dimension(0, 90)
                cell(scroll).comment("按 manifest.header.name 合并显示，默认全部勾选").align(Align.FILL)
            }

            row("外部组件：") {
                externalPackList = CheckBoxList()
                externalPackList.emptyText.text = "未在开发者启动器的行为包/资源包目录下发现合法的包"
                val scroll = JBScrollPane(externalPackList)
                scroll.preferredSize = java.awt.Dimension(0, 90)
                cell(scroll).comment("从启动器的 behavior_packs 与 resource_packs 扫描，默认全部不勾选").align(Align.FILL)
            }

            collapsibleGroup("世界设置") {
                row("存档名称：") {
                    // 编辑卡片：输入框
                    worldFolderField = JBTextField()
                    worldFolderField.emptyText.text = "留空则使用随机 UUID"
                    val editCard = JPanel(BorderLayout()).apply {
                        add(worldFolderField, BorderLayout.CENTER)
                    }

                    // 只读卡片：超链接 + 删除按钮
                    worldFolderLink = ActionLink("") { openWorldFolderInExplorer() }
                    worldFolderDeleteBtn = JButton(AllIcons.Actions.GC).apply {
                        toolTipText = "移动至回收站"
                        isContentAreaFilled = false
                        isBorderPainted = false
                        isFocusPainted = false
                        addActionListener { deleteWorldFolder() }
                    }
                    val viewCard = JPanel(FlowLayout(FlowLayout.LEADING, 4, 0)).apply {
                        add(worldFolderLink)
                        add(worldFolderDeleteBtn)
                    }

                    // 用 CardLayout 切换：collapsibleGroup 展开时不会覆盖内部 component 可见性
                    worldFolderCards = CardLayout()
                    worldFolderPanel = JPanel(worldFolderCards).apply {
                        add(editCard, WORLD_CARD_EDIT)
                        add(viewCard, WORLD_CARD_VIEW)
                    }
                    cell(worldFolderPanel).align(Align.FILL)
                }

                row("世界种子：") {
                    worldSeedField = textField().align(Align.FILL).component
                    worldSeedField.emptyText.text = "默认随机种子"
                }

                row("用户名称：") {
                    userNameField = textField().align(Align.FILL).component
                    userNameField.emptyText.text = "DevOps"
                }

                groupRowsRange("游戏规则：", true, false) {
                    row {
                        gameModeField = comboBox(
                            EnumComboBoxModel(GameMode::class.java),
                            textListCellRenderer { it?.displayName }
                        ).label("游戏模式：").component

                        levelTypeField = comboBox(
                            EnumComboBoxModel(LevelType::class.java),
                            textListCellRenderer { it?.displayName }
                        ).label("世界类型：").component
                    }

                    row {
                        playerPermissionsLevelField = comboBox(
                            EnumComboBoxModel(PlayerPermissionLevel::class.java),
                            textListCellRenderer { it?.displayName }
                        ).label("默认玩家权限：").component
                    }

                    row {
                        enableCheatsField = checkBox("启用作弊").component
                        keepInventoryField = checkBox("死亡不掉落").component
                        doDaylightCycleField = checkBox("昼夜循环").component
                        doWeatherCycleField = checkBox("天气变化").component
                        showCoordinatesField = checkBox("显示坐标").component
                    }

                    // 主面板：只读表格预览 + 编辑按钮；实际编辑走独立 DialogWrapper 弹窗
                    // 只读 JBTable 不进入 editing，不会被 transferFocus 抢焦点
                    flatLayersPreviewModel = object : javax.swing.table.DefaultTableModel(arrayOf("方块名", "层数"), 0) {
                        override fun isCellEditable(row: Int, column: Int) = false
                    }
                    flatLayersPreviewTable = com.intellij.ui.table.JBTable(flatLayersPreviewModel)
                    flatLayersPreviewTable.setShowGrid(true)
                    flatLayersPreviewTable.rowSelectionAllowed = false
                    flatLayersPreviewTable.columnSelectionAllowed = false
                    flatLayersPreviewTable.cellSelectionEnabled = false
                    flatLayersPreviewTable.emptyText.text = "无层，运行时将生成单层 minecraft:air"
                    flatLayersPreviewTable.columnModel.getColumn(1).preferredWidth = 60
                    flatLayersPreviewTable.columnModel.getColumn(1).maxWidth = 100

                    flatLayersLabelRow = row("超平坦层：") {
                        val editBtn = JButton("编辑...").apply {
                            addActionListener { openFlatLayersEditor() }
                        }
                        cell(editBtn)
                    }
                    flatLayersTableRow = row {
                        val scroll = JBScrollPane(flatLayersPreviewTable).apply {
                            preferredSize = java.awt.Dimension(400, 160)
                        }
                        cell(scroll).align(Align.FILL).comment("自上而下 = 世界自顶到底")
                    }

                    // 世界类型切换时联动显隐（label 行 + 表格行）
                    levelTypeField.addActionListener {
                        val visible = levelTypeField.selectedItem == LevelType.FLAT
                        flatLayersLabelRow.visible(visible)
                        flatLayersTableRow.visible(visible)
                    }
                }
            }.expanded = true
        }
    }

    override fun createEditor(): JComponent {
        return runConfig
    }

    override fun resetEditorFrom(config: MCRunConfiguration) {
        // 直接把配置的值写到组件上，保证 UI 刷新
        if (!config.options.gameExecutablePath.isNullOrEmpty()) {
            gameExeField.text = config.options.gameExecutablePath
        }

        logLevel.selectedItem = config.options.logLevel

        // 扫描项目包与外部包，填充两个 CheckBoxList
        val projectNames = scanProjectPackNames(config)
        val externalNames = scanExternalPackNames().filter { it !in projectNames }.toSortedSet()

        val unchecked = config.options.uncheckedProjectPackNames.toSet()
        projectPackList.clear()
        for (name in projectNames) {
            projectPackList.addItem(name, name, name !in unchecked)
        }

        val checked = config.options.checkedExternalPackNames.toSet()
        externalPackList.clear()
        for (name in externalNames) {
            externalPackList.addItem(name, name, name in checked)
        }

        // 存档目录存在则显示为不可编辑的链接，不存在则显示为可编辑输入框
        val worldFolderName = config.options.worldFolderName
        val worldsDir = PathUtils.worldsDir()
        val folder = worldsDir?.resolve(worldFolderName)
        val exists = folder != null && Files.exists(folder)

        worldFolderField.text = worldFolderName
        worldFolderLink.text = worldFolderName
        worldFolderEditable = !exists
        worldFolderCards.show(worldFolderPanel, if (exists) WORLD_CARD_VIEW else WORLD_CARD_EDIT)

        worldSeedField.text = config.options.worldSeed.toString()
        userNameField.text = config.options.userName

        gameModeField.selectedItem = config.options.gameMode
        levelTypeField.selectedItem = config.options.levelType

        enableCheatsField.isSelected = config.options.enableCheats
        keepInventoryField.isSelected = config.options.keepInventory
        doDaylightCycleField.isSelected = config.options.doDaylightCycle
        doWeatherCycleField.isSelected = config.options.doWeatherCycle
        showCoordinatesField.isSelected = config.options.showCoordinates
        playerPermissionsLevelField.selectedItem = config.options.playerPermissionsLevel

        // 首次使用（dirty=false 且存储为空）填示例，否则按存储填
        val layers = config.options.flatWorldLayers
        flatLayersCurrent = if (!config.options.flatWorldLayersDirty && layers.isEmpty()) {
            DEFAULT_FLAT_LAYERS.toMutableList()
        } else {
            layers.toMutableList()
        }
        refreshFlatLayersPreview()
        val flatVisible = config.options.levelType == LevelType.FLAT
        flatLayersLabelRow.visible(flatVisible)
        flatLayersTableRow.visible(flatVisible)
    }

    override fun applyEditorTo(config: MCRunConfiguration) {
        // 从组件读取最新值写回配置
        if (gameExeField.text.isBlank()) {
            throw ConfigurationException("启动程序路径不能为空", "配置错误")
        }
        config.options.gameExecutablePath = gameExeField.text

        config.options.logLevel = logLevel.selectedItem as LogLevel

        // 项目包：记录被反勾的
        val uncheckedProject = mutableListOf<String>()
        for (i in 0 until projectPackList.itemsCount) {
            val name = projectPackList.getItemAt(i) ?: continue
            if (!projectPackList.isItemSelected(i)) {
                uncheckedProject.add(name)
            }
        }
        config.options.uncheckedProjectPackNames = uncheckedProject

        // 外部包：记录被勾选的
        val checkedExternal = mutableListOf<String>()
        for (i in 0 until externalPackList.itemsCount) {
            val name = externalPackList.getItemAt(i) ?: continue
            if (externalPackList.isItemSelected(i)) {
                checkedExternal.add(name)
            }
        }
        config.options.checkedExternalPackNames = checkedExternal

        // 存档名：仅当处于编辑模式时才从输入框取值；只读模式下保留原值
        if (worldFolderEditable) {
            val input = worldFolderField.text.trim()
            config.options.worldFolderName = input.ifEmpty { UUID.randomUUID().toString() }
        }

        if (worldSeedField.text.isNullOrEmpty()) {
            worldSeedField.text = Random.nextLong().toString()
        }
        config.options.worldSeed = worldSeedField.text.toLong()

        config.options.userName = userNameField.text

        config.options.gameMode = gameModeField.selectedItem as GameMode
        config.options.levelType = levelTypeField.selectedItem as LevelType

        // 超平坦层直接从已维护的 flatLayersCurrent（NBT 顺序）写回
        config.options.flatWorldLayers = flatLayersCurrent.toMutableList()
        config.options.flatWorldLayersDirty = true

        config.options.enableCheats = enableCheatsField.isSelected
        config.options.keepInventory = keepInventoryField.isSelected
        config.options.doDaylightCycle = doDaylightCycleField.isSelected
        config.options.doWeatherCycle = doWeatherCycleField.isSelected
        config.options.showCoordinates = showCoordinatesField.isSelected
        config.options.playerPermissionsLevel = playerPermissionsLevelField.selectedItem as PlayerPermissionLevel
    }

    private fun refreshFlatLayersPreview() {
        // UI 预览自上而下（顶 → 底），NBT 顺序自底向上，需反转
        flatLayersPreviewModel.rowCount = 0
        for (entry in flatLayersCurrent.asReversed()) {
            val (n, c) = parseLayerEntry(entry) ?: continue
            flatLayersPreviewModel.addRow(arrayOf<Any>(n, c.toString()))
        }
    }

    private fun openFlatLayersEditor() {
        val dialog = FlatLayersEditorDialog(flatLayersCurrent)
        if (dialog.showAndGet()) {
            flatLayersCurrent = dialog.getResult().toMutableList()
            refreshFlatLayersPreview()
        }
    }

    /**
     * 独立弹窗，用 JBTable 编辑超平坦层。
     * 位于 DialogWrapper 下，不受 Kotlin UI DSL DialogPanel 的 focus traversal 影响。
     */
    private class FlatLayersEditorDialog(initial: List<String>) : com.intellij.openapi.ui.DialogWrapper(true) {
        private val model = javax.swing.table.DefaultTableModel(arrayOf("方块名", "层数"), 0)
        private val table = com.intellij.ui.table.JBTable(model)

        init {
            title = "编辑超平坦层"
            // UI 顺序（自上而下）与 NBT 相反
            for (entry in initial.asReversed()) {
                val (n, c) = parseLayerEntry(entry) ?: continue
                model.addRow(arrayOf<Any>(n, c.toString()))
            }
            table.setShowGrid(true)
            table.columnModel.getColumn(1).preferredWidth = 60
            table.columnModel.getColumn(1).maxWidth = 100
            init()
        }

        override fun createCenterPanel(): JComponent {
            val toolbar = com.intellij.ui.ToolbarDecorator.createDecorator(table)
                .setAddAction {
                    if (table.isEditing) table.cellEditor?.stopCellEditing()
                    model.addRow(arrayOf<Any>("minecraft:stone", "1"))
                }
                .setRemoveAction {
                    if (table.isEditing) table.cellEditor?.stopCellEditing()
                    for (r in table.selectedRows.sortedDescending()) model.removeRow(r)
                }
                .setMoveUpAction {
                    if (table.isEditing) table.cellEditor?.stopCellEditing()
                    val r = table.selectedRow
                    if (r > 0) {
                        model.moveRow(r, r, r - 1)
                        table.selectionModel.setSelectionInterval(r - 1, r - 1)
                    }
                }
                .setMoveDownAction {
                    if (table.isEditing) table.cellEditor?.stopCellEditing()
                    val r = table.selectedRow
                    if (r in 0 until model.rowCount - 1) {
                        model.moveRow(r, r, r + 1)
                        table.selectionModel.setSelectionInterval(r + 1, r + 1)
                    }
                }
                .addExtraAction(object : com.intellij.openapi.actionSystem.AnAction(
                    "一键清空",
                    "清空所有超平坦层",
                    AllIcons.Actions.GC
                ) {
                    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
                    override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                        e.presentation.isEnabled = model.rowCount > 0
                    }
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                        if (table.isEditing) table.cellEditor?.stopCellEditing()
                        model.rowCount = 0
                    }
                })
                .setPreferredSize(java.awt.Dimension(400, 240))
                .createPanel()
            val panel = JPanel(BorderLayout())
            panel.add(toolbar, BorderLayout.CENTER)
            val hint = javax.swing.JLabel(
                "<html>" +
                    "自上而下 = 世界自顶到底<br/>" +
                    "⚠ 基岩版引擎会忽略『仅有单层实体方块』的超平坦配置并回退默认地形，建议至少保留 2 层<br/>" +
                    "全部删除后运行时将兜底生成单层 minecraft:air" +
                    "</html>"
            )
            hint.border = javax.swing.BorderFactory.createEmptyBorder(4, 4, 0, 4)
            panel.add(hint, BorderLayout.SOUTH)
            return panel
        }

        /** 返回 NBT 顺序（自底向上）的列表 */
        fun getResult(): List<String> {
            if (table.isEditing) table.cellEditor?.stopCellEditing()
            val uiRows = mutableListOf<String>()
            for (i in 0 until model.rowCount) {
                val name = model.getValueAt(i, 0)?.toString()?.trim().orEmpty()
                val count = model.getValueAt(i, 1)?.toString()?.trim()?.toIntOrNull() ?: 0
                if (name.isNotEmpty() && count > 0) {
                    uiRows += "$name=$count"
                }
            }
            return uiRows.asReversed()
        }
    }

    private fun openWorldFolderInExplorer() {
        val worldsDir = PathUtils.worldsDir() ?: return
        val name = worldFolderLink.text
        if (name.isEmpty()) return
        val folder = worldsDir.resolve(name)
        if (Files.exists(folder)) {
            RevealFileAction.openDirectory(folder.toFile())
        }
    }

    private fun deleteWorldFolder() {
        val worldsDir = PathUtils.worldsDir() ?: return
        val name = worldFolderLink.text
        if (name.isEmpty()) return
        val folder = worldsDir.resolve(name)
        if (!Files.exists(folder)) return

        val result = Messages.showYesNoDialog(
            "确定要将存档 \"$name\" 移动至回收站吗？",
            "删除存档",
            "删除",
            "取消",
            AllIcons.General.WarningDialog
        )
        if (result == Messages.YES) {
            Desktop.getDesktop().moveToTrash(folder.toFile())
            // 切回编辑模式，保留原名方便复用
            worldFolderField.text = name
            worldFolderEditable = true
            worldFolderCards.show(worldFolderPanel, WORLD_CARD_EDIT)
        }
    }

    private fun scanProjectPackNames(config: MCRunConfiguration): Set<String> {
        val basePath = config.project.basePath ?: return emptySet()
        val packMap = java.util.EnumMap<PackUtils.PackType, MutableList<PackUtils.PackInfo>>(PackUtils.PackType::class.java)
        try {
            PackUtils.parsePack(Paths.get(basePath), packMap)
        } catch (e: Exception) {
            logger.warn("扫描项目包失败：${e.message}", e)
            return emptySet()
        }
        return packMap.values.flatten().map { it.name }.filter { it.isNotEmpty() }.toSortedSet()
    }

    private fun scanExternalPackNames(): Set<String> {
        val bp = PathUtils.behaviorPacksDir()
        val rp = PathUtils.resourcePacksDir()
        logger.info("扫描外部包：bp=$bp, rp=$rp")
        return try {
            PackUtils.scanPacksInDirs(bp, rp)
                .map { it.name }
                .filter { it.isNotEmpty() }
                .toSortedSet()
        } catch (e: Exception) {
            logger.warn("扫描外部包失败：${e.message}", e)
            emptySet()
        }
    }
}
