package dev.zerokotlin.system.controller

import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.MenuNode
import dev.zerokotlin.system.dto.MenuRequest
import dev.zerokotlin.system.security.MENU_WRITE
import dev.zerokotlin.system.service.SystemMenuService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "菜单管理")
class SystemMenuController(private val menus: SystemMenuService) {
    /**
     * 读取管理用菜单树。
     *
     * @return 从根节点展开的菜单树，含停用节点。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:read')")
    @Operation(
        summary = "获取完整菜单树",
        description = "需要 `system:menu:read` 权限。包含已停用的菜单，同级节点按排序值和 ID 升序排列。",
        responses = [ApiResponse(responseCode = "200", description = "完整菜单树", useReturnTypeSchema = true)],
    )
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
    @Operation(
        summary = "创建菜单",
        description = "需要 `$MENU_WRITE` 权限。省略 parentId 或传 null 时创建根节点。可指定站内路径和已有权限点。",
        responses = [
            ApiResponse(responseCode = "201", description = "菜单已创建", useReturnTypeSchema = true),
            ApiResponse(responseCode = "400", description = "菜单字段、父节点、路径或关联权限点无效", content = [Content()]),
        ],
    )
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
    @Operation(
        summary = "更新菜单",
        description = "需要 `$MENU_WRITE` 权限。可调整菜单层级和启用状态，父子关系须保持无环。",
        parameters = [Parameter(name = "id", `in` = ParameterIn.PATH, description = "菜单 ID")],
        responses = [
            ApiResponse(responseCode = "204", description = "菜单已更新", content = [Content()]),
            ApiResponse(responseCode = "400", description = "菜单字段、父节点、路径或关联权限点无效", content = [Content()]),
            ApiResponse(responseCode = "404", description = "菜单不存在", content = [Content()]),
        ],
    )
    fun update(@PathVariable id: String, @RequestBody request: MenuRequest) = menus.update(id, request)
}
