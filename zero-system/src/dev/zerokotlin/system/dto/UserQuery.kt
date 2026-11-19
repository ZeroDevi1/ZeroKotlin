package dev.zerokotlin.system.dto

import dev.zerokotlin.system.security.SystemActor

class UserQuery(
    actor: SystemActor,
    authority: String,
    val departmentId: String? = null,
    val roleId: String? = null,
) {
    val scope: String = actor.requireScope(authority).name
    val actorId: String = actor.user.id
    val actorDepartmentId: String? = actor.user.departmentId
}
