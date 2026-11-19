package dev.zerokotlin.system.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import dev.zerokotlin.system.entity.MenuRecord
import org.apache.ibatis.annotations.Mapper

/** 菜单节点的数据访问接口，提供树组装所需的层级和可见性配置。 */
@Mapper
interface MenuMapper : BaseMapper<MenuRecord>
