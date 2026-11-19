CREATE TABLE sys_position (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CHECK (enabled IN (0, 1))
);

CREATE INDEX idx_sys_position_sort ON sys_position (sort_order, id);

CREATE TABLE sys_user_position (
    position_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (position_id, user_id),
    FOREIGN KEY (position_id) REFERENCES sys_position (id),
    FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_user_position_user ON sys_user_position (user_id);

CREATE TABLE sys_announcement (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    title VARCHAR(120) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    published_at BIGINT,
    CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status IN ('PUBLISHED', 'REVOKED') AND published_at IS NOT NULL)
    )
);

CREATE INDEX idx_sys_announcement_created ON sys_announcement (created_at, id);
CREATE INDEX idx_sys_announcement_published ON sys_announcement (status, published_at, id);

CREATE TABLE sys_announcement_read (
    user_id VARCHAR(36) NOT NULL,
    announcement_id VARCHAR(36) NOT NULL,
    read_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, announcement_id),
    FOREIGN KEY (user_id) REFERENCES sys_user (id),
    FOREIGN KEY (announcement_id) REFERENCES sys_announcement (id)
);

CREATE INDEX idx_sys_announcement_read_notice ON sys_announcement_read (announcement_id);

INSERT INTO sys_permission (code, name) VALUES
    ('system:position:read', '查看岗位'),
    ('system:position:write', '管理岗位'),
    ('system:announcement:read', '查看公告管理'),
    ('system:announcement:write', '管理公告');

INSERT INTO sys_menu (id, name, path, permission_code, sort_order)
SELECT seed.menu_id, seed.label, seed.route, seed.permission_code, seed.display_order
FROM (
    SELECT 'system-positions' AS menu_id, '岗位管理' AS label, '/positions' AS route,
           'system:position:read' AS permission_code, 30 AS display_order
    UNION ALL SELECT 'system-announcements', '公告管理', '/announcements', 'system:announcement:read', 40
    UNION ALL SELECT 'system-inbox', '收件箱', '/inbox', NULL, 50
) seed
LEFT JOIN sys_menu existing ON existing.id = seed.menu_id OR existing.path = seed.route
WHERE existing.id IS NULL;
