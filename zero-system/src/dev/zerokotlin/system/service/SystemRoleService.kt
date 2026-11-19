package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreateRoleRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.RoleDetails
import dev.zerokotlin.system.dto.RoleMenus
import dev.zerokotlin.system.dto.RoleMenusRequest
import dev.zerokotlin.system.dto.RoleSummary
import dev.zerokotlin.system.dto.UpdateRoleRequest
import dev.zerokotlin.system.entity.RoleRecord

/** 提供角色及其权限和菜单配置相关的业务操作。 */
interface SystemRoleService : IService<RoleRecord> {
    fun list(window: PageWindow, enabled: Boolean?): PageResult<RoleSummary>
    fun detail(id: String): RoleDetails
    fun create(request: CreateRoleRequest): CreatedResource
    fun update(id: String, request: UpdateRoleRequest)
    fun menuSettings(id: String): RoleMenus
    fun updateMenus(id: String, request: RoleMenusRequest)
    fun delete(ids: List<String?>)
}
