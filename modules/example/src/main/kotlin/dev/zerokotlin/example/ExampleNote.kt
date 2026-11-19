package dev.zerokotlin.example

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "example_note")
class ExampleNote(
    @field:Id
    @field:Column(length = 36)
    var id: String,
    @field:Column(name = "owner_id", nullable = false, length = 36, updatable = false)
    var ownerId: String,
    @field:Column(nullable = false, length = 120)
    var title: String,
    @field:Column(nullable = false, length = 2000)
    var content: String,
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Long,
)

interface ExampleNoteRepository : JpaRepository<ExampleNote, String> {
    fun findByIdAndOwnerId(id: String, ownerId: String): ExampleNote?
    fun findAllByOwnerId(ownerId: String, pageable: Pageable): Page<ExampleNote>
}
