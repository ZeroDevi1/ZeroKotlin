package dev.zerokotlin.core

/** 页码从 1 开始，total 表示筛选后的记录数。 */
data class PageResult<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int,
)
