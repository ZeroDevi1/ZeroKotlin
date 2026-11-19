package dev.zerokotlin.system.dto

/**
 * 新建字典的请求。
 *
 * @property code 字典的唯一编码，供业务查询字典值。
 * @property name 字典的显示名称。
 */
data class CreateDictionaryRequest(val code: String, val name: String)

/**
 * 修改字典名称和启用状态的请求。
 *
 * @property name 更新后的字典显示名称。
 * @property enabled 是否启用字典，必填。停用后无法按编码查询其选项。
 */
data class UpdateDictionaryRequest(val name: String, val enabled: Boolean?)

/**
 * 新增或修改字典选项的请求。
 *
 * @property value 业务使用的选项值，在同一字典内唯一。
 * @property label 供界面展示的选项标签。
 * @property sortOrder 列表排序值，较小值排在前面，默认 `0`。
 * @property enabled 是否可出现在按字典编码查询的选项列表中，默认启用。
 */
data class DictionaryItemRequest(val value: String, val label: String, val sortOrder: Int = 0, val enabled: Boolean = true)

/**
 * 字典管理列表中的摘要。
 *
 * @property id 字典 ID，用于管理字典及其选项。
 * @property code 业务查询使用的唯一编码。
 * @property name 字典的显示名称。
 * @property enabled 字典是否启用。
 */
data class DictionarySummary(val id: String, val code: String, val name: String, val enabled: Boolean)

/**
 * 查询返回的一条字典选项。
 *
 * @property id 字典选项 ID。
 * @property value 业务存储和传输的选项值。
 * @property label 选项的显示标签。
 * @property sortOrder 字典内的排序值，较小值排在前面。
 * @property enabled 字典选项是否启用。
 */
data class DictionaryItem(val id: String, val value: String, val label: String, val sortOrder: Int, val enabled: Boolean)
