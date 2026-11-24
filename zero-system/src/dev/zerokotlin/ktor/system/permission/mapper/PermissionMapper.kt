package dev.zerokotlin.ktor.system.permission.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.ktor.system.permission.entity.PermissionRecord

/** 单表增查改使用 BaseMapper，连接由系统事务提供。 */
internal interface PermissionMapper : BaseMapper<PermissionRecord>
