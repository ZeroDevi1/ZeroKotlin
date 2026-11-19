package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.dto.MenuRequest
import dev.zerokotlin.system.entity.MenuRecord
import dev.zerokotlin.system.security.SystemActor

/** 提供管理菜单与会话导航相关的业务操作。 */
interface SystemMenuService : IService<MenuRecord> {
    fun tree(): List<MenuNode>
    fun visibleTree(actor: SystemActor): List<MenuNode>
    fun create(request: MenuRequest): CreatedResource
    fun update(id: String, request: MenuRequest)
}
