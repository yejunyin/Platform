-- ============================================================
-- WMS 料架库存快照表 · SQL Server 建表脚本
-- 目标库: HLEIMS2026082008001
--
-- 用途: 存 shelf/inventory 接口返回的快照，用于前后代对比
-- 设计: generation 字段轮替（偶数=prev, 奇数=curr），每次刷新只清当前代
-- ============================================================

USE HLEIMS2026082008001
GO

PRINT '================ WMS 料架库存快照 ================';
IF OBJECT_ID('dbo.wms_shelf_snapshot', 'U') IS NOT NULL
    DROP TABLE dbo.wms_shelf_snapshot;
GO

CREATE TABLE dbo.wms_shelf_snapshot (
    id              BIGINT          NOT NULL,
    generation      INT             NOT NULL,           -- 快照代次：1=首次, 2=第二次, 轮流递增（用于前后对比）
    shelf_code      VARCHAR(64)     NULL,               -- 货架编码
    production_no   VARCHAR(64)     NOT NULL,           -- 生产订单号（ProdctionNo，接口原始拼写）
    material_code   VARCHAR(64)     NOT NULL,           -- 物料编码
    quantity        DECIMAL(18,4)   NOT NULL DEFAULT 0, -- 料架上该物料数量
    create_time     DATETIME2       NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT PK_wms_shelf_snapshot PRIMARY KEY CLUSTERED (id ASC),
    INDEX IX_wss_generation     NONCLUSTERED (generation),
    INDEX IX_wss_prod_material  NONCLUSTERED (production_no, material_code),
    INDEX IX_wss_shelf          NONCLUSTERED (shelf_code)
);
GO

PRINT '========== 验证 ==========';
SELECT 'wms_shelf_snapshot' AS tbl, COUNT(*) AS cnt FROM dbo.wms_shelf_snapshot;
GO
