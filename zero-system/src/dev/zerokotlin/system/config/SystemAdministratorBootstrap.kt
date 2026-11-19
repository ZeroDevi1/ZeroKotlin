package dev.zerokotlin.system.config

import dev.zerokotlin.core.security.hashPassword
import dev.zerokotlin.system.entity.UserRecord
import dev.zerokotlin.system.mapper.SystemRelationMapper
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.security.ADMIN_ROLE_ID
import dev.zerokotlin.system.service.SystemWrites
import dev.zerokotlin.system.support.acceptableNewPassword
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.normalizeUsername
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 在应用启动时按配置初始化首个系统管理员。账号表为空时，用户及其内置管理员角色在同一事务写入。
 *
 * @property users 检查账号库是否已有用户并写入首个管理员。
 * @property relations 为首个账号写入内置管理员角色关联。
 * @property writes 在检查账号数量前获取事务级写入锁。
 * @property passwords 对通过校验的初始密码生成哈希。
 * @property username 配置提供的初始管理员用户名，与初始密码同时设置。
 * @property password 配置提供的初始管理员密码，与初始用户名同时设置。
 */
@Component
class SystemAdministratorBootstrap(
    private val users: UserMapper,
    private val relations: SystemRelationMapper,
    private val writes: SystemWrites,
    private val passwords: PasswordEncoder,
    @param:Value("\${zero.system.admin.username:}") private val username: String,
    @param:Value("\${zero.system.admin.password:}") private val password: String,
) : ApplicationRunner {
    /**
     * 读取启动配置并按需初始化管理员。两项配置均为空时跳过初始化，仅提供一项时启动失败。
     *
     * @param args Spring Boot 提供的应用启动参数，初始化凭据通过配置属性注入。
     * @throws IllegalArgumentException 仅提供一项配置，或空账号库初始化时用户名或密码格式不合要求。
     */
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (username.isEmpty() && password.isEmpty()) return
        require(username.isNotEmpty() && password.isNotEmpty()) {
            "首次初始化必须同时设置 ZERO_SYSTEM_ADMIN_USERNAME 和 ZERO_SYSTEM_ADMIN_PASSWORD"
        }
        writes.lock()
        if (users.selectCount(null) != 0L) return

        val normalized = requireNotNull(normalizeUsername(username)) { "首次管理员用户名格式无效" }
        require(acceptableNewPassword(password)) { "首次管理员密码长度无效" }

        val id = newId()
        users.insert(UserRecord(id, normalized, "系统管理员", passwords.hashPassword(password)))
        relations.insertUserRole(id, ADMIN_ROLE_ID)
    }
}
