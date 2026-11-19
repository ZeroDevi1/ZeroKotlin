CREATE TABLE sys_write_guard (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    revision BIGINT NOT NULL
);

INSERT INTO sys_write_guard (id, revision) VALUES ('system', 0);

CREATE TABLE sys_department (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    parent_id VARCHAR(36),
    name VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (parent_id) REFERENCES sys_department (id)
);

CREATE INDEX idx_sys_department_parent ON sys_department (parent_id);

CREATE TABLE sys_user (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    department_id VARCHAR(36),
    enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CHECK (enabled IN (0, 1))
);

CREATE INDEX idx_sys_user_department ON sys_user (department_id);

CREATE TABLE sys_role (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    data_scope VARCHAR(32) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    CHECK (data_scope IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_AND_DESCENDANTS', 'ALL')),
    CHECK (enabled IN (0, 1))
);

CREATE TABLE sys_permission (
    code VARCHAR(120) PRIMARY KEY NOT NULL,
    name VARCHAR(120) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    CHECK (enabled IN (0, 1))
);

CREATE TABLE sys_user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user (id),
    FOREIGN KEY (role_id) REFERENCES sys_role (id)
);

CREATE INDEX idx_sys_user_role_role ON sys_user_role (role_id, user_id);

CREATE TABLE sys_role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    FOREIGN KEY (role_id) REFERENCES sys_role (id),
    FOREIGN KEY (permission_code) REFERENCES sys_permission (code)
);

CREATE TABLE sys_menu (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    module_name VARCHAR(32),
    parent_id VARCHAR(36),
    name VARCHAR(120) NOT NULL,
    path VARCHAR(255),
    permission_code VARCHAR(120),
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (parent_id) REFERENCES sys_menu (id),
    FOREIGN KEY (permission_code) REFERENCES sys_permission (code),
    CHECK (enabled IN (0, 1))
);

CREATE INDEX idx_sys_menu_parent ON sys_menu (parent_id);

CREATE TABLE sys_dictionary (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    CHECK (enabled IN (0, 1))
);

CREATE TABLE sys_dictionary_item (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    dictionary_id VARCHAR(36) NOT NULL,
    item_value VARCHAR(120) NOT NULL,
    label VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    UNIQUE (dictionary_id, item_value),
    FOREIGN KEY (dictionary_id) REFERENCES sys_dictionary (id),
    CHECK (enabled IN (0, 1))
);

CREATE TABLE sys_session (
    digest VARCHAR(64) PRIMARY KEY NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    expires_at BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_session_user ON sys_session (user_id);
CREATE INDEX idx_sys_session_expiry ON sys_session (expires_at);

CREATE TABLE sys_login_audit (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    user_id VARCHAR(36),
    username VARCHAR(64),
    successful INTEGER NOT NULL,
    remote_address VARCHAR(128) NOT NULL,
    occurred_at BIGINT NOT NULL,
    CHECK (successful IN (0, 1))
);

CREATE INDEX idx_sys_login_audit_time ON sys_login_audit (occurred_at, id);

CREATE TABLE sys_operation_audit (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    action VARCHAR(120) NOT NULL,
    target_id VARCHAR(120) NOT NULL,
    occurred_at BIGINT NOT NULL
);

CREATE INDEX idx_sys_operation_audit_time ON sys_operation_audit (occurred_at, id);

INSERT INTO sys_role (id, code, name, data_scope)
VALUES ('system-admin', 'system-admin', '系统管理员', 'ALL');

INSERT INTO sys_permission (code, name) VALUES
    ('system:user:read', '查看用户'),
    ('system:user:update', '修改用户资料'),
    ('system:user:manage', '管理用户账号'),
    ('system:department:read', '查看部门'),
    ('system:department:write', '管理部门'),
    ('system:role:read', '查看角色'),
    ('system:role:write', '管理角色'),
    ('system:permission:read', '查看权限'),
    ('system:permission:write', '管理权限'),
    ('system:menu:read', '查看菜单'),
    ('system:menu:write', '管理菜单'),
    ('system:dictionary:read', '查看字典'),
    ('system:dictionary:write', '管理字典'),
    ('system:audit:read', '查看审计');
