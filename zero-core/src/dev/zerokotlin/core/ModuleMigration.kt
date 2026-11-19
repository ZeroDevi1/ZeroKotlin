package dev.zerokotlin.core

/**
 * 可选模块的 Flyway 迁移声明。每个模块使用独立的迁移目录和历史表。
 *
 * @property name 同时用于迁移目录和历史表的后缀，发布后应保持稳定。长度为 1 至 32，
 *   首字符为小写英文字母，其余字符可为小写英文字母、数字或下划线。
 */
data class ModuleMigration(val name: String) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9_]{0,31}"))) { "Invalid migration module name: $name" }
    }
}
