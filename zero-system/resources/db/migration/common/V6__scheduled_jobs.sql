CREATE TABLE sys_scheduled_job (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    name VARCHAR(120) NOT NULL,
    handler VARCHAR(120) NOT NULL,
    schedule_json TEXT,
    anchor_at BIGINT,
    next_fire_at BIGINT,
    paused INTEGER NOT NULL DEFAULT 0,
    CHECK (paused IN (0, 1)),
    CHECK (
        (schedule_json IS NULL AND anchor_at IS NULL AND next_fire_at IS NULL AND paused = 0)
        OR (schedule_json IS NOT NULL AND anchor_at IS NOT NULL AND (paused = 0 OR next_fire_at IS NULL))
    )
);

CREATE INDEX idx_sys_scheduled_job_next ON sys_scheduled_job (next_fire_at, id);

CREATE TABLE sys_job_run (
    id VARCHAR(36) PRIMARY KEY NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    job_name VARCHAR(120) NOT NULL,
    handler VARCHAR(120) NOT NULL,
    actor_id VARCHAR(36),
    source VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_at BIGINT NOT NULL,
    started_at BIGINT,
    finished_at BIGINT,
    message VARCHAR(500),
    CHECK (
        (source = 'MANUAL' AND actor_id IS NOT NULL)
        OR (source = 'SCHEDULED' AND actor_id IS NULL AND status IN ('SUCCESS', 'FAILED'))
    ),
    CHECK (
        (status = 'QUEUED' AND started_at IS NULL AND finished_at IS NULL)
        OR (status = 'CANCELLED' AND started_at IS NULL AND finished_at IS NOT NULL)
        OR (status IN ('SUCCESS', 'FAILED') AND started_at IS NOT NULL AND finished_at IS NOT NULL)
    )
);

CREATE INDEX idx_sys_job_run_time ON sys_job_run (requested_at, id);
CREATE INDEX idx_sys_job_run_job_time ON sys_job_run (job_id, requested_at, id);
CREATE INDEX idx_sys_job_run_queue ON sys_job_run (status, requested_at, id);

INSERT INTO sys_permission (code, name)
SELECT seed.code, seed.name
FROM (
    SELECT 'system:job:read' AS code, '查看定时任务' AS name
    UNION ALL SELECT 'system:job:create', '创建定时任务'
    UNION ALL SELECT 'system:job:update', '编辑定时任务'
    UNION ALL SELECT 'system:job:delete', '删除定时任务'
    UNION ALL SELECT 'system:job:pause', '暂停定时任务'
    UNION ALL SELECT 'system:job:resume', '恢复定时任务'
    UNION ALL SELECT 'system:job:run', '立即执行定时任务'
    UNION ALL SELECT 'system:job:log:read', '查看定时任务执行日志'
) seed
LEFT JOIN sys_permission existing ON existing.code = seed.code
WHERE existing.code IS NULL;

INSERT INTO sys_menu (id, parent_id, name, path, permission_code, sort_order)
SELECT seed.menu_id, 'zero-nav-system', '定时任务', seed.route, 'system:job:read', 50
FROM (SELECT 'zero-nav-jobs' AS menu_id, '/jobs' AS route) seed
LEFT JOIN sys_menu existing ON existing.id = seed.menu_id OR existing.path = seed.route
WHERE existing.id IS NULL;
