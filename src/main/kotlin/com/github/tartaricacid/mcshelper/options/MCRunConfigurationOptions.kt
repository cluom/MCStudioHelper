package com.github.tartaricacid.mcshelper.options

import com.intellij.execution.configurations.RunConfigurationOptions
import java.util.*
import kotlin.random.Random

class MCRunConfigurationOptions : RunConfigurationOptions() {
    private val gameExecutablePathProperty = string("").provideDelegate(this, "gameExecutablePath")

    private val logLevelProperty = enum(LogLevel.NORMAL).provideDelegate(this, "logLevel")

    // 项目内组件：默认全部勾选，此列表保存被显式反勾的包名
    private val uncheckedProjectPackNamesProperty =
        list<String>().provideDelegate(this, "uncheckedProjectPackNames")
    // 外部组件：默认全部不勾选，此列表保存被显式勾选的包名
    private val checkedExternalPackNamesProperty =
        list<String>().provideDelegate(this, "checkedExternalPackNames")

    private val worldFolderNameProperty = string(UUID.randomUUID().toString()).provideDelegate(this, "worldFolderName")
    private val worldSeedProperty = property(Random.nextLong()).provideDelegate(this, "worldSeed")
    private val userNameProperty = string("DevOps").provideDelegate(this, "userName")

    private val gameModeProperty = enum(GameMode.CREATIVE).provideDelegate(this, "gameMode")
    private val levelTypeProperty = enum(LevelType.DEFAULT).provideDelegate(this, "levelType")

    private val enableCheatsProperty = property(true).provideDelegate(this, "enableCheats")
    private val keepInventoryProperty = property(false).provideDelegate(this, "keepInventory")
    private val doDaylightCycleProperty = property(true).provideDelegate(this, "doDaylightCycle")
    private val doWeatherCycleProperty = property(true).provideDelegate(this, "doWeatherCycle")

    var gameExecutablePath: String?
        get() = gameExecutablePathProperty.getValue(this)
        set(path) = gameExecutablePathProperty.setValue(this, path)

    var logLevel: LogLevel
        get() = logLevelProperty.getValue(this)
        set(level) = logLevelProperty.setValue(this, level)

    var uncheckedProjectPackNames: MutableList<String>
        get() = uncheckedProjectPackNamesProperty.getValue(this)
        set(list) {
            uncheckedProjectPackNamesProperty.setValue(this, list)
        }

    var checkedExternalPackNames: MutableList<String>
        get() = checkedExternalPackNamesProperty.getValue(this)
        set(list) {
            checkedExternalPackNamesProperty.setValue(this, list)
        }

    var worldFolderName: String
        get() = worldFolderNameProperty.getValue(this) ?: UUID.randomUUID().toString()
        set(value) = worldFolderNameProperty.setValue(this, value)

    var worldSeed: Long
        get() = worldSeedProperty.getValue(this)
        set(value) = worldSeedProperty.setValue(this, value)

    var userName: String
        get() = userNameProperty.getValue(this) ?: "DevOps"
        set(value) = userNameProperty.setValue(this, value)

    var gameMode: GameMode
        get() = gameModeProperty.getValue(this)
        set(value) = gameModeProperty.setValue(this, value)

    var levelType: LevelType
        get() = levelTypeProperty.getValue(this)
        set(value) = levelTypeProperty.setValue(this, value)

    var enableCheats: Boolean
        get() = enableCheatsProperty.getValue(this)
        set(value) = enableCheatsProperty.setValue(this, value)

    var keepInventory: Boolean
        get() = keepInventoryProperty.getValue(this)
        set(value) = keepInventoryProperty.setValue(this, value)

    var doDaylightCycle: Boolean
        get() = doDaylightCycleProperty.getValue(this)
        set(value) = doDaylightCycleProperty.setValue(this, value)

    var doWeatherCycle: Boolean
        get() = doWeatherCycleProperty.getValue(this)
        set(value) = doWeatherCycleProperty.setValue(this, value)
}
