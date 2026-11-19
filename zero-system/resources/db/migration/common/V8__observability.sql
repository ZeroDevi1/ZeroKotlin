ALTER TABLE sys_login_audit ADD COLUMN ip_region VARCHAR(255);

CREATE TABLE sys_request_audit (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    actor_id VARCHAR(36),
    action VARCHAR(120) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    route_pattern VARCHAR(255) NOT NULL,
    http_status INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    exception_type VARCHAR(120),
    remote_address VARCHAR(128) NOT NULL,
    ip_region VARCHAR(255),
    user_agent VARCHAR(512),
    occurred_at BIGINT NOT NULL
);

CREATE INDEX idx_sys_request_audit_time ON sys_request_audit (occurred_at, id);

INSERT INTO sys_permission (code, name)
SELECT seed.code, seed.label
FROM (SELECT 'system:monitor:read' AS code, '查看运行监控' AS label) seed
LEFT JOIN sys_permission existing ON existing.code = seed.code
WHERE existing.code IS NULL;

INSERT INTO sys_menu (id, parent_id, name, path, permission_code, sort_order)
SELECT seed.menu_id, parent.id, '运行监控', seed.route, 'system:monitor:read', 40
FROM (SELECT 'zero-nav-operations' AS menu_id, '/operations' AS route) seed
LEFT JOIN sys_menu parent ON parent.id = 'zero-nav-system'
LEFT JOIN sys_menu existing ON existing.id = seed.menu_id OR existing.path = seed.route
WHERE existing.id IS NULL;
