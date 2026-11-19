CREATE TABLE sys_file (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    biz_type VARCHAR(120),
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    storage_backend VARCHAR(8) NOT NULL,
    storage_location_id VARCHAR(64) NOT NULL,
    state VARCHAR(12) NOT NULL,
    uploaded_by VARCHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    CHECK (size_bytes >= 0),
    CHECK (storage_backend IN ('LOCAL', 'MINIO', 'ALIYUN')),
    CHECK (state IN ('UPLOADING', 'AVAILABLE', 'DELETING'))
);

CREATE INDEX idx_sys_file_created ON sys_file (created_at, id);

INSERT INTO sys_permission (code, name)
SELECT seed.permission_code, seed.label
FROM (
    SELECT 'system:file:read' AS permission_code, '查看文件列表' AS label
    UNION ALL SELECT 'system:file:upload', '上传文件'
    UNION ALL SELECT 'system:file:download', '下载文件'
    UNION ALL SELECT 'system:file:delete', '删除文件'
) seed
LEFT JOIN sys_permission existing ON existing.code = seed.permission_code
WHERE existing.code IS NULL;

INSERT INTO sys_menu (id, parent_id, name, path, permission_code, sort_order)
SELECT seed.menu_id, 'zero-nav-system', '文件管理', seed.route, 'system:file:read', 30
FROM (SELECT 'system-files' AS menu_id, '/files' AS route) seed
LEFT JOIN sys_menu existing ON existing.id = seed.menu_id OR existing.path = seed.route
WHERE existing.id IS NULL;
