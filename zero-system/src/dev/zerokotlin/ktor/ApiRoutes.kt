package dev.zerokotlin.ktor

import dev.zerokotlin.core.security.LoginCaptchaService
import dev.zerokotlin.core.security.SecurityState
import dev.zerokotlin.core.storage.FileStorages
import dev.zerokotlin.ktor.system.account.AccountServiceImpl
import dev.zerokotlin.ktor.system.account.UserMapper
import dev.zerokotlin.ktor.system.account.accountController
import dev.zerokotlin.ktor.system.announcement.AnnouncementMapper
import dev.zerokotlin.ktor.system.announcement.AnnouncementServiceImpl
import dev.zerokotlin.ktor.system.announcement.announcementController
import dev.zerokotlin.ktor.system.audit.AuditService
import dev.zerokotlin.ktor.system.audit.auditController
import dev.zerokotlin.ktor.system.audit.MonitorService
import dev.zerokotlin.ktor.system.audit.monitorController
import dev.zerokotlin.ktor.system.auth.AuthService
import dev.zerokotlin.ktor.system.auth.authController
import dev.zerokotlin.ktor.system.department.DepartmentMapper
import dev.zerokotlin.ktor.system.department.DepartmentServiceImpl
import dev.zerokotlin.ktor.system.department.departmentController
import dev.zerokotlin.ktor.system.dictionary.DictionaryItemMapper
import dev.zerokotlin.ktor.system.dictionary.DictionaryMapper
import dev.zerokotlin.ktor.system.dictionary.DictionaryServiceImpl
import dev.zerokotlin.ktor.system.dictionary.dictionaryController
import dev.zerokotlin.ktor.system.excel.ExcelService
import dev.zerokotlin.ktor.system.excel.excelController
import dev.zerokotlin.ktor.system.extension.ExtensionSettings
import dev.zerokotlin.ktor.system.extension.ExtensionService
import dev.zerokotlin.ktor.system.extension.extensionController
import dev.zerokotlin.ktor.system.file.FileMapper
import dev.zerokotlin.ktor.system.file.FileService
import dev.zerokotlin.ktor.system.file.fileController
import dev.zerokotlin.ktor.system.inbox.InboxService
import dev.zerokotlin.ktor.system.inbox.inboxController
import dev.zerokotlin.ktor.system.job.JobService
import dev.zerokotlin.ktor.system.job.jobController
import dev.zerokotlin.ktor.system.menu.MenuMapper
import dev.zerokotlin.ktor.system.menu.MenuServiceImpl
import dev.zerokotlin.ktor.system.menu.menuController
import dev.zerokotlin.ktor.system.permission.controller.permissionController
import dev.zerokotlin.ktor.system.permission.mapper.PermissionMapper
import dev.zerokotlin.ktor.system.permission.service.impl.PermissionServiceImpl
import dev.zerokotlin.ktor.system.position.PositionMapper
import dev.zerokotlin.ktor.system.position.PositionServiceImpl
import dev.zerokotlin.ktor.system.position.positionController
import dev.zerokotlin.ktor.system.realtime.RealtimeService
import dev.zerokotlin.ktor.system.realtime.realtimeController
import dev.zerokotlin.ktor.system.role.RoleMapper
import dev.zerokotlin.ktor.system.role.RoleServiceImpl
import dev.zerokotlin.ktor.system.role.roleController
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/** /api 下的完整装配顺序。Controller 文件声明子路径并连接对应实现。 */
internal fun Route.apiRoutes(
    store: SystemStore,
    captcha: LoginCaptchaService,
    files: FileStorages,
    security: SecurityState,
    extensions: ExtensionSettings,
    modules: List<BusinessModule>,
) {
    authController(AuthService(store, captcha), security)
    inboxController(InboxService(store))
    realtimeController(RealtimeService(store))
    route("system") {
        val accounts = AccountServiceImpl(store)
        permissionController(PermissionServiceImpl(store))
        roleController(RoleServiceImpl(store))
        menuController(MenuServiceImpl(store))
        departmentController(DepartmentServiceImpl(store), accounts)
        accountController(accounts)
        positionController(PositionServiceImpl(store))
        dictionaryController(DictionaryServiceImpl(store))
        announcementController(AnnouncementServiceImpl(store))
        auditController(AuditService(store))
        monitorController(MonitorService(store, security))
        fileController(FileService(store, files))
        excelController(ExcelService(store))
        jobController(JobService(store))
    }
    extensionController(ExtensionService(store, extensions))
    businessRoutes(store, modules)
}

/** 与系统 Controller 同处一个装配入口，应用启动时一次性注册 Mapper。 */
internal val systemMapperTypes = setOf(
    PermissionMapper::class.java,
    UserMapper::class.java,
    RoleMapper::class.java,
    MenuMapper::class.java,
    DepartmentMapper::class.java,
    PositionMapper::class.java,
    AnnouncementMapper::class.java,
    FileMapper::class.java,
    DictionaryMapper::class.java,
    DictionaryItemMapper::class.java,
)
