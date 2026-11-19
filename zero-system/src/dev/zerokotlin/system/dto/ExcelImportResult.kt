package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 整批导入提交后的结果。
 *
 * @property imported 已创建的记录数，不含表头或空白行。
 */
@Schema(description = "整批导入提交后的结果。")
data class ExcelImportResult(
    @field:Schema(description = "已创建的记录数。", minimum = "1")
    val imported: Int,
)
