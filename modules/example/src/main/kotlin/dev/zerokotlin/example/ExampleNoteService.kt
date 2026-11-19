package dev.zerokotlin.example

import dev.zerokotlin.core.CurrentActor
import dev.zerokotlin.core.OperationAudit
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

private const val READ_NOTES = "example:note:read"
private const val CREATE_NOTE = "example:note:create"

@Service
@Transactional(readOnly = true)
class ExampleNoteService(
    private val notes: ExampleNoteRepository,
    private val currentActor: CurrentActor,
    private val audit: OperationAudit,
) {
    @PreAuthorize("hasAuthority('$READ_NOTES')")
    fun list(page: Int, size: Int): PageResult<ExampleNoteView> {
        val window = PageWindow(page, size)
        val order = Sort.by(Sort.Direction.DESC, "createdAt", "id")
        val result = notes.findAllByOwnerId(currentActor.requireUserId(), PageRequest.of(page - 1, size, order))
        return window.result(result.content.map(ExampleNote::toView), result.totalElements)
    }

    @PreAuthorize("hasAuthority('$READ_NOTES')")
    fun get(id: String): ExampleNoteView =
        notes.findByIdAndOwnerId(id, currentActor.requireUserId())?.toView()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "未找到示例笔记")

    @Transactional
    @PreAuthorize("hasAuthority('$CREATE_NOTE')")
    fun create(request: CreateExampleNote): ExampleNoteView {
        val title = request.title.trim()
        if (title.isEmpty() || title.length > 120 || request.content.length > 2000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "标题需为 1 至 120 字符，正文最多 2000 字符")
        }
        val id = UUID.randomUUID().toString()
        audit.record(CREATE_NOTE, id)
        val note = notes.save(
            ExampleNote(id, currentActor.requireUserId(), title, request.content, System.currentTimeMillis())
        )
        return note.toView()
    }
}

private fun ExampleNote.toView() = ExampleNoteView(id, title, content, createdAt)

data class CreateExampleNote(val title: String, val content: String)
data class ExampleNoteView(val id: String, val title: String, val content: String, val createdAt: Long)
