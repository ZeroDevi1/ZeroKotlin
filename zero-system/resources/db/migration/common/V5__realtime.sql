INSERT INTO sys_permission (code, name)
SELECT seed.code, seed.name
FROM (
    SELECT 'system:message:read' AS code, '读取实时消息' AS name
    UNION ALL SELECT 'system:message:broadcast', '群发实时消息'
    UNION ALL SELECT 'system:message:private', '私发实时消息'
) seed
LEFT JOIN sys_permission existing ON existing.code = seed.code
WHERE existing.code IS NULL;

INSERT INTO sys_menu (id, parent_id, name, path, permission_code, sort_order)
SELECT 'system-realtime', parent.id, '实时消息', '/realtime', 'system:message:read', 50
FROM sys_menu parent
LEFT JOIN sys_menu existing ON existing.id = 'system-realtime' OR existing.path = '/realtime'
WHERE parent.id = 'zero-nav-system'
  AND existing.id IS NULL;
