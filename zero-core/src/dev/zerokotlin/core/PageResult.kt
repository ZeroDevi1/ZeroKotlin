package dev.zerokotlin.core

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * 分页查询返回给调用方的数据和分页信息。
 *
 * @param T 单条记录的类型。
 * @property items 当前页的记录。
 * @property total 筛选后、分页前的记录总数。
 * @property page 当前页码，从 1 开始。
 * @property size 请求的每页条数上限。
 */
@Schema(description = "分页查询返回给调用方的数据和分页信息。")
data class PageResult<T>(
    @field:Schema(description = "当前页的记录。")
    val items: List<T>,
    @field:Schema(description = "筛选后、分页前的记录总数。", minimum = "0")
    val total: Long,
    @field:Schema(description = "当前页码，从 1 开始。", minimum = "1", example = "1")
    val page: Int,
    @field:Schema(description = "请求的每页条数上限。", minimum = "1", maximum = "100", example = "20")
    val size: Int,
)

/**
 * 已校验的分页窗口。页码或每页条数越界时，构造即抛出 HTTP 400 异常。
 *
 * @property page 请求页码，最小值为 1。
 * @property size 请求的每页条数，范围为 1 至 100。
 */
class PageWindow(val page: Int, val size: Int) {
    init {
        if (page < 1 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page 必须大于等于 1，size 必须介于 1 和 100 之间")
        }
    }

    /** 查询当前页前应跳过的记录数，首页为 0。 */
    val offset: Long = (page - 1L) * size

    /**
     * 将查询结果与当前窗口的分页参数一起返回。
     *
     * @param T 单条记录的类型。
     * @param items 当前页的记录。
     * @param total 使用相同筛选条件得到的记录总数。
     * @return 包含页码、每页条数、总记录数和当前页记录的分页结果。
     */
    fun <T> result(items: List<T>, total: Long): PageResult<T> = PageResult(items, total, page, size)
}
