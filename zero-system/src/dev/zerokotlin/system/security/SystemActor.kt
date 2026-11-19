package dev.zerokotlin.system.security

import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.entity.DataScope
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

class SystemActor(
    val user: UserRow,
    val sessionDigest: String,
    val administrator: Boolean,
    val scopes: Map<String, DataScope>,
) {
    fun requireScope(authority: String): DataScope =
        scopes[authority] ?: throw AccessDeniedException("缺少权限 $authority")

    fun requireAdministrator() {
        if (!administrator) throw AccessDeniedException("仅系统管理员可执行此操作")
    }
}

fun currentSystemActor(): SystemActor =
    SecurityContextHolder.getContext().authentication?.principal as? SystemActor
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
