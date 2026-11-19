package dev.zerokotlin.system.dto

data class CreateDictionaryRequest(val code: String, val name: String)

data class UpdateDictionaryRequest(val name: String, val enabled: Boolean?)

data class DictionaryItemRequest(val value: String, val label: String, val sortOrder: Int = 0, val enabled: Boolean = true)

data class DictionarySummary(val id: String, val code: String, val name: String, val enabled: Boolean)

data class DictionaryItem(val id: String, val value: String, val label: String, val sortOrder: Int, val enabled: Boolean)
