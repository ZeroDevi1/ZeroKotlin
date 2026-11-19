package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CurrentUserResponse
import dev.zerokotlin.system.dto.LoginRequest
import dev.zerokotlin.system.dto.LoginResponse
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.service.SystemAuthentication
import dev.zerokotlin.system.service.SystemMenus
import dev.zerokotlin.system.service.SystemSessionTransactions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class SystemAuthenticationController(
    private val authentication: SystemAuthentication,
    private val sessions: SystemSessionTransactions,
    private val menus: SystemMenus,
) {
    @PostMapping("/login")
    fun login(@RequestBody credentials: LoginRequest, request: HttpServletRequest): ResponseEntity<LoginResponse> {
        val result = authentication.login(credentials, request.remoteAddr.take(128))
        return if (result == null) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build()
        } else {
            ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result)
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() = sessions.logout()

    @GetMapping("/me")
    fun me(): CurrentUserResponse {
        val actor = currentSystemActor()
        return CurrentUserResponse(
            actor.user.id, actor.user.username, actor.user.displayName, actor.user.departmentId, actor.scopes,
            actor.administrator,
        )
    }

    @GetMapping("/me/menus")
    fun menus(): List<MenuNode> = menus.visibleTree(currentSystemActor())
}
