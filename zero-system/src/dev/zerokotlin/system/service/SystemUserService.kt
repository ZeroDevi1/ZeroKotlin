package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreateUserRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.OwnProfile
import dev.zerokotlin.system.dto.OwnProfileRequest
import dev.zerokotlin.system.dto.ResetPasswordRequest
import dev.zerokotlin.system.dto.UserAccountRequest
import dev.zerokotlin.system.dto.UserDetails
import dev.zerokotlin.system.dto.UserListEntry
import dev.zerokotlin.system.dto.UserProfileRequest
import dev.zerokotlin.system.dto.UserSummary
import dev.zerokotlin.system.entity.UserRecord
import dev.zerokotlin.system.support.ExcelImportRow

/** 提供用户账号及资料相关的业务操作。 */
interface SystemUserService : IService<UserRecord> {
    fun list(window: PageWindow, departmentId: String?, roleId: String?): PageResult<UserListEntry>
    fun forExcel(departmentId: String?, roleId: String?): List<UserListEntry>
    fun detail(id: String): UserDetails
    fun ownProfile(): OwnProfile
    fun updateOwnProfile(request: OwnProfileRequest): OwnProfile
    fun create(request: CreateUserRequest): CreatedResource
    fun importExcel(rows: List<ExcelImportRow<CreateUserRequest>>)
    fun updateProfile(id: String, request: UserProfileRequest): UserSummary
    fun updateAccount(id: String, request: UserAccountRequest)
    fun resetPassword(id: String, request: ResetPasswordRequest)
    fun delete(ids: List<String?>)
}
