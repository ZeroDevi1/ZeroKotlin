package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.dto.MenuRequest
import dev.zerokotlin.system.security.MENU_WRITE
import dev.zerokotlin.system.service.SystemMenus
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 提供管理端的菜单层级查询和维护接口。
 *
 * @property menus 读取菜单树并维护节点、路由和权限关联。
 */
@RestController
@RequestMapping("/api/system/menus")
class SystemMenuController(private val menus: SystemMenus) {
    /**
     * 读取管理用菜单树。
     *
     * @return 从根节点展开的菜单树，含停用节点。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:read')")
    fun tree(): List<MenuNode> = menus.tree()

    /**
     * 创建一个菜单节点。
     *
     * @param request 节点的名称、上级菜单、站内路径、权限点、排序及启用状态。
     * @return HTTP 201 响应体，包含新资源的 ID。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$MENU_WRITE')")
    fun create(@RequestBody request: MenuRequest): CreatedResource = menus.create(request)

    /**
     * 修改菜单节点及其层级，成功时返回 HTTP 204。
     *
     * @param id 待修改的菜单 ID。
     * @param request 菜单的新属性和上级菜单。
     */
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$MENU_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: MenuRequest) = menus.update(id, request)
}
