CREATE TABLE example_note (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_example_note_owner_created ON example_note (owner_id, created_at, id);

INSERT INTO sys_permission (code, name) VALUES
    ('example:note:read', '查看示例笔记'),
    ('example:note:create', '创建示例笔记');

INSERT INTO sys_menu (id, module_name, name, path, permission_code, sort_order)
VALUES ('example-notes', 'example', '示例笔记', '/example/notes', 'example:note:read', 100);
