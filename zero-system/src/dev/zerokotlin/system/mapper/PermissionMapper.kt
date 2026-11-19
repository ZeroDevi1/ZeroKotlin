package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.PermissionRecord
import org.apache.ibatis.annotations.Mapper

/** 权限点的数据访问接口，用于维护定义和查询参与有效授权的权限点。 */
@Mapper
interface PermissionMapper : BaseMapper<PermissionRecord>
