-- ============================================================
-- 仅创建叫料记录与预警两张表（不影响已有的 mes_dwd_material_detail）
-- ============================================================
USE HLEIMS2026082008001
GO

PRINT '================ 叫料记录 ================';
IF OBJECT_ID('dbo.mes_dwd_material_call', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.mes_dwd_material_call (
        id             INT              IDENTITY(1,1) NOT NULL,
        task_id        VARCHAR(64)      NOT NULL,
        material_code  VARCHAR(64)      NOT NULL,
        material_name  NVARCHAR(200)    NOT NULL,
        required_qty   INT              NOT NULL,
        call_qty       INT              NOT NULL,
        call_type      VARCHAR(20)      NOT NULL,   -- normal/urgent
        caller         NVARCHAR(50)     NOT NULL,
        call_time      DATETIME2        NOT NULL,
        status         VARCHAR(20)      NOT NULL,   -- pending/delivering/delivered
        responder      NVARCHAR(50)     NULL,
        response_time  DATETIME2        NULL,
        deliver_time   DATETIME2        NULL,
        remark         NVARCHAR(500)    NULL,
        CONSTRAINT PK_mes_dwd_material_call PRIMARY KEY CLUSTERED (id ASC)
    );
    CREATE INDEX IX_mc_task_id  ON dbo.mes_dwd_material_call(task_id);
    CREATE INDEX IX_mc_status   ON dbo.mes_dwd_material_call(status);
    PRINT '已创建表 mes_dwd_material_call';
END
ELSE
    PRINT '表 mes_dwd_material_call 已存在，跳过';
GO

PRINT '================ 预警 ================';
IF OBJECT_ID('dbo.mes_dwd_material_alert', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.mes_dwd_material_alert (
        id          INT              IDENTITY(1,1) NOT NULL,
        task_id     VARCHAR(64)      NOT NULL,
        type        VARCHAR(20)      NOT NULL,   -- shortage/low_stock/overtime
        level       VARCHAR(20)      NOT NULL,   -- danger/warning/info
        message     NVARCHAR(500)    NOT NULL,
        status      VARCHAR(20)      NOT NULL,   -- active/resolved
        created_at  DATETIME2        NOT NULL,
        CONSTRAINT PK_mes_dwd_material_alert PRIMARY KEY CLUSTERED (id ASC)
    );
    CREATE INDEX IX_ma_task_id ON dbo.mes_dwd_material_alert(task_id);
    CREATE INDEX IX_ma_status  ON dbo.mes_dwd_material_alert(status);
    PRINT '已创建表 mes_dwd_material_alert';
END
ELSE
    PRINT '表 mes_dwd_material_alert 已存在，跳过';
GO

PRINT '========== 验证统计 ==========';
SELECT 'material_detail' AS tbl, COUNT(*) AS cnt FROM dbo.mes_dwd_material_detail
UNION ALL
SELECT 'material_call'  AS tbl, COUNT(*) AS cnt FROM dbo.mes_dwd_material_call
UNION ALL
SELECT 'material_alert' AS tbl, COUNT(*) AS cnt FROM dbo.mes_dwd_material_alert;
GO
