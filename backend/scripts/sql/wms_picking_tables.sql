-- ============================================================
-- WMS 叫料（生产领料申请）· SQL Server 建表脚本
-- 目标库: HLEIMS2026082008001
-- 包含: 领料单头 / 领料明细
-- ============================================================

USE HLEIMS2026082008001
GO

PRINT '================ WMS 领料单头 ================';
IF OBJECT_ID('dbo.wms_picking', 'U') IS NOT NULL
    DROP TABLE dbo.wms_picking;
GO

CREATE TABLE dbo.wms_picking (
    id              BIGINT          NOT NULL,
    bill_no         VARCHAR(32)     NOT NULL,          -- MES 侧单号（MES 前缀 + 序号）
    production_no   VARCHAR(64)     NOT NULL,          -- 生产订单号
    product_code    VARCHAR(64)     NULL,              -- 产品编码（冗余）
    product_name    NVARCHAR(200)   NULL,              -- 产品名称（冗余）
    line_code       VARCHAR(64)     NOT NULL,          -- 生产线编码（用户发送时填写）
    org_code        VARCHAR(32)     NULL,              -- 组织编码
    dept_code       VARCHAR(32)     NULL,              -- 部门编码
    status          VARCHAR(16)     NOT NULL DEFAULT 'pending', -- pending/sent/failed
    wms_task_no     VARCHAR(64)     NULL,              -- WMS 返回任务号
    message         NVARCHAR(500)   NULL,              -- 成功/失败消息
    request_id      VARCHAR(64)     NULL,              -- 发给 WMS 的 RequestId
    send_time       DATETIME2       NULL,
    create_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    update_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    deleted         INT             NOT NULL DEFAULT 0,
    CONSTRAINT PK_wms_picking PRIMARY KEY CLUSTERED (id ASC),
    INDEX IX_wp_production_no NONCLUSTERED (production_no),
    INDEX IX_wp_status NONCLUSTERED (status)
);
GO

PRINT '================ WMS 领料明细 ================';
IF OBJECT_ID('dbo.wms_picking_entry', 'U') IS NOT NULL
    DROP TABLE dbo.wms_picking_entry;
GO

CREATE TABLE dbo.wms_picking_entry (
    id              BIGINT          NOT NULL,
    picking_id      BIGINT          NOT NULL,
    item_no         INT             NOT NULL,
    material_code   VARCHAR(64)     NOT NULL,
    material_name   NVARCHAR(200)   NULL,
    plan_qty        DECIMAL(18,4)   NULL,       -- 金蝶应发数量
    picked_qty      DECIMAL(18,4)   NOT NULL,   -- 实际领料数量（默认=plan_qty）
    unit_code       VARCHAR(20)     NULL,
    plan_code       VARCHAR(64)     NULL,
    station_code    VARCHAR(32)     NULL,
    warehouse_code  VARCHAR(32)     NULL,
    remark          NVARCHAR(500)   NULL,
    create_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    update_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    deleted         INT             NOT NULL DEFAULT 0,
    CONSTRAINT PK_wms_picking_entry PRIMARY KEY CLUSTERED (id ASC),
    INDEX IX_wpe_picking_id NONCLUSTERED (picking_id)
);
GO

PRINT '========== 验证 ==========';
SELECT 'wms_picking' AS tbl, COUNT(*) AS cnt FROM dbo.wms_picking
UNION ALL
SELECT 'wms_picking_entry' AS tbl, COUNT(*) AS cnt FROM dbo.wms_picking_entry;
GO
