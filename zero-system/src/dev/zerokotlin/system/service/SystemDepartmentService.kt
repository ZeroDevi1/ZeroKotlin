package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.DepartmentNode
import dev.zerokotlin.system.dto.DepartmentRequest
import dev.zerokotlin.system.entity.DepartmentRecord

/** 提供部门树与部门资料相关的业务操作。 */
interface SystemDepartmentService : IService<DepartmentRecord> {
    fun tree(): List<DepartmentNode>
    fun create(request: DepartmentRequest): CreatedResource
    fun update(id: String, request: DepartmentRequest)
}
