package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.DepartmentRecord
import org.apache.ibatis.annotations.Mapper

/** 部门节点的数据访问接口，为组织树查询和层级维护提供持久化操作。 */
@Mapper
interface DepartmentMapper : BaseMapper<DepartmentRecord>
