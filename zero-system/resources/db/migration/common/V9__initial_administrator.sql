-- 在同一连接中记住迁移开始时是否为空库，避免给已有账号补授管理员角色。
CREATE TEMPORARY TABLE zero_v9_initial_admin (id VARCHAR(36) PRIMARY KEY);

INSERT INTO zero_v9_initial_admin (id)
SELECT '6d33c3bf-9c98-4675-8a55-35b72619a6e0'
WHERE '${zeroSkipDefaultAdmin}' <> 'true'
  AND NOT EXISTS (SELECT 1 FROM sys_user);

-- 默认账号 admin，密码 admin。仅写入成本因子为 12 的 BCrypt 哈希。
INSERT INTO sys_user (id, username, display_name, password_hash, enabled)
SELECT id, 'admin', '系统管理员',
       '$2y$12$lJI.YrCLNnYiAEABbO7A7e6wZRooq4i5e96Te3RROaaji762JWlRG', 1
FROM zero_v9_initial_admin;

INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 'system-admin' FROM zero_v9_initial_admin;

DROP TABLE zero_v9_initial_admin;
