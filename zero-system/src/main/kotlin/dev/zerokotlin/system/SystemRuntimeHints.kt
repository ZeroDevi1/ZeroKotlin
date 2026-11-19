package dev.zerokotlin.system

import org.springframework.aot.hint.MemberCategory.ACCESS_DECLARED_FIELDS
import org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
import org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

class SystemRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("mapper/system/*.xml")
        hints.resources().registerPattern("db/migration/common/V1__system.sql")
        listOf(
            UserRecord::class.java,
            DepartmentRecord::class.java,
            RoleRecord::class.java,
            PermissionRecord::class.java,
            MenuRecord::class.java,
            DictionaryRecord::class.java,
            DictionaryItemRecord::class.java,
            SessionRecord::class.java,
            LoginAuditRecord::class.java,
            OperationAuditRecord::class.java,
            UserRow::class.java,
            AuthorityRow::class.java,
            AssignedRoleRow::class.java,
            PageWindow::class.java,
            UserQuery::class.java,
        ).forEach {
            hints.reflection().registerType(it, ACCESS_DECLARED_FIELDS, INVOKE_DECLARED_CONSTRUCTORS, INVOKE_PUBLIC_METHODS)
        }
    }
}
