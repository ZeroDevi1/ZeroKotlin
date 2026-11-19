package dev.zerokotlin.core

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** 页码从 1 开始，total 表示筛选后的记录数。 */
data class PageResult<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int,
)

class PageWindow(val page: Int, val size: Int) {
    init {
        if (page < 1 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page 必须大于等于 1，size 必须介于 1 和 100 之间")
        }
    }

    val offset: Long = (page - 1L) * size

    fun <T> result(items: List<T>, total: Long): PageResult<T> = PageResult(items, total, page, size)
}
