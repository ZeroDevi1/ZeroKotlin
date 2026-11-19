package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.dto.MenuRequest
import dev.zerokotlin.system.security.MENU_WRITE
import dev.zerokotlin.system.service.SystemMenus
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
@RequestMapping("/api/system/menus")
class SystemMenuController(private val menus: SystemMenus) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:read')")
    fun tree(): List<MenuNode> = menus.tree()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$MENU_WRITE')")
    fun create(@RequestBody request: MenuRequest): CreatedResource = menus.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$MENU_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: MenuRequest) = menus.update(id, request)
}
