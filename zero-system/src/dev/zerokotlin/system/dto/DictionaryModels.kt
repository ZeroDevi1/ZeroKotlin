package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 新建字典的请求。
 *
 * @property code 字典的唯一编码，供业务查询字典值。
 * @property name 字典的显示名称。
 */
@Schema(description = "新建字典的请求。")
data class CreateDictionaryRequest(
    @field:Schema(description = "字典的唯一编码，供业务查询字典值。")
    val code: String,
    @field:Schema(description = "字典的显示名称。")
    val name: String,
)

/**
 * 修改字典名称和启用状态的请求。
 *
 * @property name 更新后的字典显示名称。
 * @property enabled 是否启用字典，必填。停用后无法按编码查询其选项。
 */
@Schema(description = "修改字典名称和启用状态的请求。")
data class UpdateDictionaryRequest(
    @field:Schema(description = "更新后的字典显示名称。")
    val name: String,
    @field:Schema(description = "是否启用字典，必填。停用后无法按编码查询其选项。", requiredMode = Schema.RequiredMode.REQUIRED)
    val enabled: Boolean?,
)

/**
 * 新增或修改字典选项的请求。
 *
 * @property value 业务使用的选项值，在同一字典内唯一。
 * @property label 供界面展示的选项标签。
 * @property sortOrder 列表排序值，较小值排在前面，默认 `0`。
 * @property enabled 是否可出现在按字典编码查询的选项列表中，默认启用。
 */
@Schema(description = "新增或修改字典选项的请求。")
data class DictionaryItemRequest(
    @field:Schema(description = "业务使用的选项值，在同一字典内唯一。")
    val value: String,
    @field:Schema(description = "供界面展示的选项标签。")
    val label: String,
    @field:Schema(description = "列表排序值，较小值排在前面，默认 `0`。")
    val sortOrder: Int = 0,
    @field:Schema(description = "是否可出现在按字典编码查询的选项列表中，默认启用。")
    val enabled: Boolean = true,
)

/**
 * 字典管理列表中的摘要。
 *
 * @property id 字典 ID，用于管理字典及其选项。
 * @property code 业务查询使用的唯一编码。
 * @property name 字典的显示名称。
 * @property enabled 字典是否启用。
 */
@Schema(description = "字典管理列表中的摘要。")
data class DictionarySummary(
    @field:Schema(description = "字典 ID，用于管理字典及其选项。")
    val id: String,
    @field:Schema(description = "业务查询使用的唯一编码。")
    val code: String,
    @field:Schema(description = "字典的显示名称。")
    val name: String,
    @field:Schema(description = "字典是否启用。")
    val enabled: Boolean,
)

/**
 * 查询返回的一条字典选项。
 *
 * @property id 字典选项 ID。
 * @property value 业务存储和传输的选项值。
 * @property label 选项的显示标签。
 * @property sortOrder 字典内的排序值，较小值排在前面。
 * @property enabled 字典选项是否启用。
 */
@Schema(description = "查询返回的一条字典选项。")
data class DictionaryItem(
    @field:Schema(description = "字典选项 ID。")
    val id: String,
    @field:Schema(description = "业务存储和传输的选项值。")
    val value: String,
    @field:Schema(description = "选项的显示标签。")
    val label: String,
    @field:Schema(description = "字典内的排序值，较小值排在前面。")
    val sortOrder: Int,
    @field:Schema(description = "字典选项是否启用。")
    val enabled: Boolean,
)
