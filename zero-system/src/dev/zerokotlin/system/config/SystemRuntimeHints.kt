package dev.zerokotlin.system.config

import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.AssignedRoleRow
import dev.zerokotlin.system.dto.AuthorityRow
import dev.zerokotlin.system.dto.InboxEntry
import dev.zerokotlin.system.dto.RealtimeMessage
import dev.zerokotlin.system.dto.UserQuery
import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.entity.AnnouncementRecord
import dev.zerokotlin.system.entity.DepartmentRecord
import dev.zerokotlin.system.entity.DictionaryItemRecord
import dev.zerokotlin.system.entity.DictionaryRecord
import dev.zerokotlin.system.entity.FileRecord
import dev.zerokotlin.system.entity.JobRunRecord
import dev.zerokotlin.system.entity.LoginAuditRecord
import dev.zerokotlin.system.entity.MenuRecord
import dev.zerokotlin.system.entity.OperationAuditRecord
import dev.zerokotlin.system.entity.PermissionRecord
import dev.zerokotlin.system.entity.PositionRecord
import dev.zerokotlin.system.entity.RoleRecord
import dev.zerokotlin.system.entity.ScheduledJobRecord
import dev.zerokotlin.system.entity.SessionRecord
import dev.zerokotlin.system.entity.UserRecord
import org.springframework.aot.hint.MemberCategory.ACCESS_DECLARED_FIELDS
import org.springframework.aot.hint.MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
import org.springframework.aot.hint.MemberCategory.INVOKE_PUBLIC_METHODS
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/** 为原生镜像登记系统模块的 MyBatis 映射、迁移脚本及持久化反射访问。 */
class SystemRuntimeHints : RuntimeHintsRegistrar {
    /**
     * 注册映射和迁移资源，以及数据类、记录类和查询类型的构造器、字段与公开方法访问。
     *
     * @param hints 接收系统模块资源和反射声明的运行时提示集合。
     * @param classLoader Spring 提供的可选应用类加载器。
     */
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("mapper/system/*.xml")
        hints.resources().registerPattern("db/migration/common/V1__system.sql")
        hints.resources().registerPattern("db/migration/common/V2__positions_and_announcements.sql")
        hints.resources().registerPattern("db/migration/common/V6__scheduled_jobs.sql")
        listOf(
            UserRecord::class.java,
            DepartmentRecord::class.java,
            PositionRecord::class.java,
            AnnouncementRecord::class.java,
            RoleRecord::class.java,
            PermissionRecord::class.java,
            MenuRecord::class.java,
            DictionaryRecord::class.java,
            DictionaryItemRecord::class.java,
            FileRecord::class.java,
            SessionRecord::class.java,
            LoginAuditRecord::class.java,
            OperationAuditRecord::class.java,
            ScheduledJobRecord::class.java,
            JobRunRecord::class.java,
            UserRow::class.java,
            AuthorityRow::class.java,
            AssignedRoleRow::class.java,
            InboxEntry::class.java,
            RealtimeMessage::class.java,
            PageWindow::class.java,
            UserQuery::class.java,
        ).forEach {
            hints.reflection().registerType(it, ACCESS_DECLARED_FIELDS, INVOKE_DECLARED_CONSTRUCTORS, INVOKE_PUBLIC_METHODS)
        }
    }
}
