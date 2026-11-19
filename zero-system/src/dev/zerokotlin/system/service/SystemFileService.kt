package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.spring.service.IService
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.FileEntry
import dev.zerokotlin.system.entity.FileRecord
import java.io.InputStream
import java.util.UUID
import org.springframework.web.multipart.MultipartFile

/** 提供文件元数据与存储对象相关的业务操作。 */
interface SystemFileService : IService<FileRecord> {
    fun list(window: PageWindow, name: String?, bizType: String?): PageResult<FileEntry>
    fun upload(file: MultipartFile, bizType: String?): FileEntry
    fun download(id: UUID, consume: (FileEntry, InputStream) -> Unit)
    fun delete(id: UUID)
}
