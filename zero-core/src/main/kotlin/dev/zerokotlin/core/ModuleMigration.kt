package dev.zerokotlin.core

/** 可选模块使用独立的迁移目录和历史表。name 在模块发布后应保持稳定。 */
data class ModuleMigration(val name: String) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9_]{0,31}"))) { "Invalid migration module name: $name" }
    }
}
