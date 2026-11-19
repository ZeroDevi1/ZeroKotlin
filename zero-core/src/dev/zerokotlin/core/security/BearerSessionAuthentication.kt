package dev.zerokotlin.core.security

import org.springframework.security.core.Authentication

fun interface BearerSessionAuthentication {
    fun authenticate(sessionDigest: String): Authentication?
}
