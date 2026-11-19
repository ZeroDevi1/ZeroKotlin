package dev.zerokotlin.core.security

import org.springframework.http.HttpMethod

data class PublicApiOperation(val method: HttpMethod, val path: String)
