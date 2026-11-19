INSERT INTO sys_menu (id, name, sort_order)
SELECT seed.menu_id, seed.label, seed.display_order
FROM (
    SELECT 'zero-nav-org' AS menu_id, '组织管理' AS label, 10 AS display_order
    UNION ALL SELECT 'zero-nav-access', '权限管理', 20
    UNION ALL SELECT 'zero-nav-system', '系统管理', 30
) seed
LEFT JOIN sys_menu existing ON existing.id = seed.menu_id
WHERE existing.id IS NULL;

-- 旧库可能已配置或停用了同一路径，保留已有菜单和授权。
INSERT INTO sys_menu (id, parent_id, name, path, permission_code, sort_order)
SELECT seed.menu_id, seed.parent_id, seed.label, seed.route, seed.permission_code, seed.display_order
FROM (
    SELECT 'zero-nav-users' AS menu_id, 'zero-nav-org' AS parent_id, '用户管理' AS label,
           '/users' AS route, 'system:user:read' AS permission_code, 10 AS display_order
    UNION ALL SELECT 'zero-nav-departments', 'zero-nav-org', '部门管理', '/departments', 'system:department:read', 20
    UNION ALL SELECT 'zero-nav-roles', 'zero-nav-access', '角色管理', '/roles', 'system:role:read', 10
    UNION ALL SELECT 'zero-nav-permissions', 'zero-nav-access', '权限点', '/permissions', 'system:permission:read', 20
    UNION ALL SELECT 'zero-nav-menus', 'zero-nav-access', '菜单管理', '/menus', 'system:menu:read', 30
    UNION ALL SELECT 'zero-nav-dictionaries', 'zero-nav-system', '数据字典', '/dictionaries', 'system:dictionary:read', 10
    UNION ALL SELECT 'zero-nav-audits', 'zero-nav-system', '审计日志', '/audits', 'system:audit:read', 20
) seed
LEFT JOIN sys_menu existing ON existing.id = seed.menu_id OR existing.path = seed.route
WHERE existing.id IS NULL;

UPDATE sys_menu SET parent_id = 'zero-nav-org'
WHERE id = 'system-positions' AND parent_id IS NULL AND path = '/positions';

UPDATE sys_menu SET parent_id = 'zero-nav-system'
WHERE id = 'system-announcements' AND parent_id IS NULL AND path = '/announcements';
