package dev.zerokotlin.example

import dev.zerokotlin.core.PageResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/example/notes")
class ExampleNoteController(private val notes: ExampleNoteService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResult<ExampleNoteView> = notes.list(page, size)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ExampleNoteView = notes.get(id)

    @PostMapping
    fun create(@RequestBody request: CreateExampleNote): ResponseEntity<ExampleNoteView> {
        val note = notes.create(request)
        return ResponseEntity.created(URI.create("/api/example/notes/${note.id}")).body(note)
    }
}
