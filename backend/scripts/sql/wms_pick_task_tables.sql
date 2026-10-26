-- ============================================================
-- 定时 WMS 领料任务 · SQL Server 建表脚本
-- 目标库: HLEIMS2026082008001
-- 包含: 任务主表 / 任务明细 / 执行日志
-- ============================================================

USE HLEIMS2026082008001
GO

PRINT '================ 定时领料任务主表 ================';
IF OBJECT_ID('dbo.wms_pick_task', 'U') IS NOT NULL
    DROP TABLE dbo.wms_pick_task;
GO

CREATE TABLE dbo.wms_pick_task (
    id              BIGINT          NOT NULL,
    task_no         VARCHAR(32)     NOT NULL,
    production_no   VARCHAR(64)     NOT NULL,
    product_code    VARCHAR(64)     NULL,
    product_name    NVARCHAR(200)   NULL,
    line_code       VARCHAR(64)     NOT NULL,
    org_code        VARCHAR(32)     NULL,
    dept_code       VARCHAR(32)     NULL,
    per_box_qty     DECIMAL(18,4)  NOT NULL DEFAULT 1,
    interval_sec    INT             NOT NULL DEFAULT 30,
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending',
    total_rounds    INT             NOT NULL DEFAULT 0,
    current_round   INT             NOT NULL DEFAULT 0,
    next_fire_time  DATETIME2       NULL,
    last_fire_time  DATETIME2       NULL,
    success_count   INT             NOT NULL DEFAULT 0,
    fail_count      INT             NOT NULL DEFAULT 0,
    message         NVARCHAR(500)   NULL,
    create_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    update_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    deleted         INT             NOT NULL DEFAULT 0,
    CONSTRAINT PK_wms_pick_task PRIMARY KEY CLUSTERED (id ASC)
);
GO

CREATE NONCLUSTERED INDEX IX_wpt_production_no ON dbo.wms_pick_task (production_no);
CREATE NONCLUSTERED INDEX IX_wpt_status ON dbo.wms_pick_task (status);
GO

PRINT '================ 定时领料任务明细 ================';
IF OBJECT_ID('dbo.wms_pick_task_entry', 'U') IS NOT NULL
    DROP TABLE dbo.wms_pick_task_entry;
GO

CREATE TABLE dbo.wms_pick_task_entry (
    id              BIGINT          NOT NULL,
    task_id         BIGINT          NOT NULL,
    item_no         INT             NOT NULL,
    material_code   VARCHAR(64)     NOT NULL,
    material_name   NVARCHAR(200)   NULL,
    plan_qty        DECIMAL(18,4)   NULL,
    total_qty       DECIMAL(18,4)   NOT NULL,
    per_box_qty     DECIMAL(18,4)   NOT NULL DEFAULT 1,
    total_rounds    INT             NOT NULL DEFAULT 0,
    current_round   INT             NOT NULL DEFAULT 0,
    sent_qty        DECIMAL(18,4)   NOT NULL DEFAULT 0,
    unit_code       VARCHAR(20)     NULL,
    plan_code       VARCHAR(64)     NULL,
    station_code    VARCHAR(32)     NULL,
    warehouse_code  VARCHAR(32)     NULL,
    create_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    update_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    deleted         INT             NOT NULL DEFAULT 0,
    CONSTRAINT PK_wms_pick_task_entry PRIMARY KEY CLUSTERED (id ASC)
);
GO

CREATE NONCLUSTERED INDEX IX_wpte_task_id ON dbo.wms_pick_task_entry (task_id);
GO

PRINT '================ 定时领料任务执行日志 ================';
IF OBJECT_ID('dbo.wms_pick_task_log', 'U') IS NOT NULL
    DROP TABLE dbo.wms_pick_task_log;
GO

CREATE TABLE dbo.wms_pick_task_log (
    id              BIGINT          NOT NULL,
    task_id         BIGINT          NOT NULL,
    round_no        INT             NOT NULL,
    bill_no         VARCHAR(32)     NOT NULL,
    wms_task_no     VARCHAR(64)     NULL,
    status          VARCHAR(16)     NOT NULL,
    request_id      VARCHAR(64)     NULL,
    message         NVARCHAR(1000)  NULL,
    create_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT PK_wms_pick_task_log PRIMARY KEY CLUSTERED (id ASC)
);
GO

CREATE NONCLUSTERED INDEX IX_wptl_task_id ON dbo.wms_pick_task_log (task_id, create_time DESC);
GO

PRINT '========== 验证 ==========';
SELECT 'wms_pick_task' AS tbl, COUNT(*) AS cnt FROM dbo.wms_pick_task
UNION ALL
SELECT 'wms_pick_task_entry' AS tbl, COUNT(*) AS cnt FROM dbo.wms_pick_task_entry
UNION ALL
SELECT 'wms_pick_task_log' AS tbl, COUNT(*) AS cnt FROM dbo.wms_pick_task_log;
GO
