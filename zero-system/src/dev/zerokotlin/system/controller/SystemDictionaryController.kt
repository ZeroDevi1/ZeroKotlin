package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreateDictionaryRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DictionaryItem
import dev.zerokotlin.system.dto.DictionaryItemRequest
import dev.zerokotlin.system.dto.DictionarySummary
import dev.zerokotlin.system.dto.UpdateDictionaryRequest
import dev.zerokotlin.system.security.DICTIONARY_WRITE
import dev.zerokotlin.system.service.SystemDictionaries
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/system/dictionaries")
class SystemDictionaryController(private val dictionaries: SystemDictionaries) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    fun list(): List<DictionarySummary> = dictionaries.list()

    @GetMapping("/by-code/{code}/items")
    fun values(@PathVariable code: String): List<DictionaryItem> = dictionaries.values(code)

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    fun items(@PathVariable id: String): List<DictionaryItem> = dictionaries.items(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun create(@RequestBody request: CreateDictionaryRequest): CreatedResource = dictionaries.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdateDictionaryRequest) = dictionaries.update(id, request)

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun createItem(@PathVariable id: String, @RequestBody request: DictionaryItemRequest): CreatedResource =
        dictionaries.createItem(id, request)

    @PutMapping("/{dictionaryId}/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$DICTIONARY_WRITE')")
    fun updateItem(
        @PathVariable dictionaryId: String,
        @PathVariable id: String,
        @RequestBody request: DictionaryItemRequest,
    ) = dictionaries.updateItem(dictionaryId, id, request)
}
