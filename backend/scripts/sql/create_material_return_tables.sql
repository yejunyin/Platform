-- ============================================================
-- 补退料（生产补料/生产退料）系统建表脚本
-- 表: DB_MATERIAL_CALL(申请主表) / DB_MATERIAL_CALL_ITEM(申请明细)
--     DB_MATERIAL_CALL_BATCH(批次匹配) / DB_MATERIAL_REASON(补料原因字典)
-- ============================================================
USE HLEIMS2026082008001
GO

PRINT '================ 申请主表 ================';
IF OBJECT_ID('dbo.DB_MATERIAL_CALL', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.DB_MATERIAL_CALL (
        ID              VARCHAR(32)     NOT NULL,            -- 申请单ID(雪花)
        CALL_NO         VARCHAR(32)     NOT NULL,            -- 申请单号 TL+时间戳
        APPLICANT_ID    VARCHAR(32)     NULL,                -- 申请人ID
        APPLICANT_CODE  VARCHAR(64)     NULL,                -- 申请人工号(staffCode)
        APPLICANT_NAME  VARCHAR(50)     NULL,                -- 申请人姓名
        APPLICANT_DEPT  VARCHAR(100)    NULL,                -- 申请人部门
        QC_STAFF_ID     VARCHAR(32)     NULL,                -- 质检员ID
        QC_STAFF_CODE   VARCHAR(64)     NULL,                -- 质检员工号
        QC_STAFF_NAME   VARCHAR(50)     NULL,                -- 质检员姓名
        REASON_ID       INT             NULL,                -- 补料原因ID
        REASON_TEXT     VARCHAR(100)    NULL,                -- 补料原因文本
        STATUS          INT             NOT NULL DEFAULT 10, -- 状态机: 10待质检 11已驳回 20批次匹配中 30退料单生成中 31退料单生成异常 40退料单已生成 41WMS申请异常 50WMS申请已生成 99已完成
        RETURN_TYPE     INT             NULL,                -- 退料类型 1良品 2不良品 3报废
        REJECT_REASON   VARCHAR(500)    NULL,                -- 驳回原因
        AUDIT_BY        VARCHAR(50)     NULL,                -- 审核人
        AUDIT_TIME      DATETIME2       NULL,                -- 审核时间
        ERP_ORDER_NO    VARCHAR(50)     NULL,                -- 金蝶退料单号
        ERP_REPLENISH_ORDER_NO VARCHAR(50) NULL,             -- 金蝶补料单号
        WMS_ORDER_NO    VARCHAR(50)     NULL,                -- WMS出库申请单号
        ERROR_MSG       VARCHAR(500)    NULL,                -- 异常信息(31/41)
        WMS_ENABLED     TINYINT         NOT NULL DEFAULT 0,  -- 是否涉及WMS仓库
        CREATE_TIME     DATETIME2       NULL,
        UPDATE_TIME     DATETIME2       NULL,
        CONSTRAINT PK_DB_MATERIAL_CALL PRIMARY KEY CLUSTERED (ID ASC)
    );
    CREATE UNIQUE INDEX UX_DBMC_CALL_NO ON dbo.DB_MATERIAL_CALL(CALL_NO);
    CREATE INDEX IX_DBMC_STATUS    ON dbo.DB_MATERIAL_CALL(STATUS);
    CREATE INDEX IX_DBMC_APPLICANT ON dbo.DB_MATERIAL_CALL(APPLICANT_CODE);
    CREATE INDEX IX_DBMC_QC        ON dbo.DB_MATERIAL_CALL(QC_STAFF_ID);
    PRINT '已创建表 DB_MATERIAL_CALL';
END
ELSE
    PRINT '表 DB_MATERIAL_CALL 已存在，跳过';
GO

PRINT '================ 申请明细表 ================';
IF OBJECT_ID('dbo.DB_MATERIAL_CALL_ITEM', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.DB_MATERIAL_CALL_ITEM (
        ID              VARCHAR(32)     NOT NULL,
        CALL_ID         VARCHAR(32)     NOT NULL,            -- 关联主表ID
        ORDER_CODE      VARCHAR(50)     NOT NULL,            -- 生产订单号
        MATERIAL_CODE   VARCHAR(50)     NOT NULL,            -- 物料编码
        MATERIAL_NAME   NVARCHAR(100)   NULL,                -- 物料名称
        SPEC            VARCHAR(100)    NULL,                -- 规格型号
        UNIT            VARCHAR(20)     NULL,                -- 单位
        QTY             DECIMAL(16,2)   NOT NULL,            -- 申请数量
        CREATE_TIME     DATETIME2       NULL,
        CONSTRAINT PK_DB_MATERIAL_CALL_ITEM PRIMARY KEY CLUSTERED (ID ASC)
    );
    CREATE INDEX IX_DBMCI_CALL_ID ON dbo.DB_MATERIAL_CALL_ITEM(CALL_ID);
    CREATE INDEX IX_DBMCI_MAT     ON dbo.DB_MATERIAL_CALL_ITEM(MATERIAL_CODE);
    PRINT '已创建表 DB_MATERIAL_CALL_ITEM';
END
ELSE
    PRINT '表 DB_MATERIAL_CALL_ITEM 已存在，跳过';
GO

PRINT '================ 批次匹配表(FIFO结果+库存预占) ================';
IF OBJECT_ID('dbo.DB_MATERIAL_CALL_BATCH', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.DB_MATERIAL_CALL_BATCH (
        ID              VARCHAR(32)     NOT NULL,
        CALL_ID         VARCHAR(32)     NOT NULL,            -- 关联主表ID
        CALL_ITEM_ID    VARCHAR(32)     NOT NULL,            -- 关联明细表ID
        MATERIAL_CODE   VARCHAR(50)     NOT NULL,            -- 物料编码
        BATCH_NO        VARCHAR(50)     NULL,                -- 批次号
        LOCATION_CODE   VARCHAR(50)     NULL,                -- 库位(账套未启用库位时为空)
        WAREHOUSE       VARCHAR(50)     NULL,                -- 仓库名称
        WAREHOUSE_CODE  VARCHAR(50)     NULL,                -- 仓库编码(金蝶FStockId.FNumber,退料单/WMS用)
        STOCK_ORG       VARCHAR(50)     NULL,                -- 库存组织编码(金蝶FStockOrgId.FNumber)
        QTY             DECIMAL(16,2)   NOT NULL,            -- 匹配数量
        WMS_FLAG        TINYINT         NOT NULL DEFAULT 0,  -- 仓库是否启用WMS
        RESERVED        TINYINT         NOT NULL DEFAULT 1,  -- 库存预占标记
        CREATE_TIME     DATETIME2       NULL,
        CONSTRAINT PK_DB_MATERIAL_CALL_BATCH PRIMARY KEY CLUSTERED (ID ASC)
    );
    CREATE INDEX IX_DBMCB_CALL_ID ON dbo.DB_MATERIAL_CALL_BATCH(CALL_ID);
    CREATE INDEX IX_DBMCB_MAT     ON dbo.DB_MATERIAL_CALL_BATCH(MATERIAL_CODE);
    CREATE INDEX IX_DBMCB_BATCH   ON dbo.DB_MATERIAL_CALL_BATCH(MATERIAL_CODE, BATCH_NO);
    PRINT '已创建表 DB_MATERIAL_CALL_BATCH';
END
ELSE
    PRINT '表 DB_MATERIAL_CALL_BATCH 已存在，跳过';
GO

PRINT '================ 补料原因字典 ================';
IF OBJECT_ID('dbo.DB_MATERIAL_REASON', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.DB_MATERIAL_REASON (
        ID          INT             IDENTITY(1,1) NOT NULL,
        REASON_TYPE INT             NOT NULL DEFAULT 1,      -- 1补料原因(预留扩展)
        NAME        NVARCHAR(100)   NOT NULL,                -- 原因名称
        SORT_NO     INT             NOT NULL DEFAULT 0,
        ENABLED     TINYINT         NOT NULL DEFAULT 1,
        CONSTRAINT PK_DB_MATERIAL_REASON PRIMARY KEY CLUSTERED (ID ASC)
    );
    INSERT INTO dbo.DB_MATERIAL_REASON (REASON_TYPE, NAME, SORT_NO) VALUES
        (1, N'生产超损耗', 1),
        (1, N'订单缺料', 2),
        (1, N'设备故障损耗', 3),
        (1, N'质量不良损耗', 4),
        (1, N'工艺更改', 5),
        (1, N'其他', 9);
    PRINT '已创建表 DB_MATERIAL_REASON 并写入初始字典';
END
ELSE
    PRINT '表 DB_MATERIAL_REASON 已存在，跳过';
GO

PRINT '========== 增量变更：申请主表加补料单号列 ================';
IF COL_LENGTH('dbo.DB_MATERIAL_CALL', 'ERP_REPLENISH_ORDER_NO') IS NULL
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_CALL ADD ERP_REPLENISH_ORDER_NO VARCHAR(50) NULL; -- 金蝶补料单号
    PRINT '已为 DB_MATERIAL_CALL 增加列 ERP_REPLENISH_ORDER_NO';
END
ELSE
    PRINT '列 ERP_REPLENISH_ORDER_NO 已存在，跳过';
GO

PRINT '========== 增量变更：批次匹配表加仓位内码列 ================';
IF COL_LENGTH('dbo.DB_MATERIAL_CALL_BATCH', 'LOCATION_ID') IS NULL
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_CALL_BATCH ADD LOCATION_ID BIGINT NULL; -- 仓位值组合内码(金蝶FStockLocId)
    PRINT '已为 DB_MATERIAL_CALL_BATCH 增加列 LOCATION_ID';
END
ELSE
    PRINT '列 LOCATION_ID 已存在，跳过';
GO

PRINT '========== 验证统计 ==========';
SELECT 'DB_MATERIAL_CALL' AS tbl, COUNT(*) AS cnt FROM dbo.DB_MATERIAL_CALL
UNION ALL
SELECT 'DB_MATERIAL_CALL_ITEM', COUNT(*) FROM dbo.DB_MATERIAL_CALL_ITEM
UNION ALL
SELECT 'DB_MATERIAL_CALL_BATCH', COUNT(*) FROM dbo.DB_MATERIAL_CALL_BATCH
UNION ALL
SELECT 'DB_MATERIAL_REASON', COUNT(*) FROM dbo.DB_MATERIAL_REASON;
GO
