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

PRINT '========== 增量变更20260911：退料类型与退料原因拆分 ================';
-- 语义调整: REASON_ID/REASON_TEXT=退料原因(字典reasonType=2, 发起人申请时选择)
--           RETURN_TYPE=退料类型字典ID(reasonType=1): 发起人提交时写入(接口5), 审核通过时以接口9回传值为准覆盖
--           RETURN_TYPE_NAME=退料类型文案冗余, 供接口8/7直接返回展示

-- 1. 申请主表新增 RETURN_TYPE_NAME
IF COL_LENGTH('dbo.DB_MATERIAL_CALL', 'RETURN_TYPE_NAME') IS NULL
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_CALL ADD RETURN_TYPE_NAME VARCHAR(50) NULL; -- 退料类型文案冗余
    PRINT '已为 DB_MATERIAL_CALL 增加列 RETURN_TYPE_NAME';
END
ELSE
    PRINT '列 RETURN_TYPE_NAME 已存在，跳过';
GO

-- 2. 字典表新增 ERP_CODE(金蝶退料原因编码 FReturnReason.FNumber, 仅退料类型 reasonType=1 使用)
IF COL_LENGTH('dbo.DB_MATERIAL_REASON', 'ERP_CODE') IS NULL
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_REASON ADD ERP_CODE VARCHAR(50) NULL;
    PRINT '已为 DB_MATERIAL_REASON 增加列 ERP_CODE';
END
ELSE
    PRINT '列 ERP_CODE 已存在，跳过';
GO

-- 3. 字典数据迁移: 原 reasonType=1 的"补料原因"类数据迁移为 reasonType=2 退料原因
--    (IDENTITY列不可UPDATE, 采用删除重插; 存量单据 REASON_TEXT 已冗余文案, 展示不受影响)
IF EXISTS(SELECT 1 FROM dbo.DB_MATERIAL_REASON WHERE REASON_TYPE = 1 AND ERP_CODE IS NULL)
BEGIN
    SELECT NAME, SORT_NO, ENABLED INTO #mig_reason FROM dbo.DB_MATERIAL_REASON
        WHERE REASON_TYPE = 1 AND ERP_CODE IS NULL;
    DELETE FROM dbo.DB_MATERIAL_REASON WHERE REASON_TYPE = 1 AND ERP_CODE IS NULL;
    INSERT INTO dbo.DB_MATERIAL_REASON (REASON_TYPE, NAME, SORT_NO, ENABLED)
        SELECT 2, NAME, SORT_NO, ENABLED FROM #mig_reason;
    DROP TABLE #mig_reason;
    PRINT '已将原补料原因数据迁移为退料原因(reasonType=2)';
END
ELSE
    PRINT '无待迁移的补料原因数据，跳过';
GO

-- 4. 重录 reasonType=1 退料类型字典(ID 即金蝶 FReturnType 枚举值: 1良品退料 2来料不良退料)
IF NOT EXISTS(SELECT 1 FROM dbo.DB_MATERIAL_REASON WHERE REASON_TYPE = 1)
BEGIN
    SET IDENTITY_INSERT dbo.DB_MATERIAL_REASON ON;
    INSERT INTO dbo.DB_MATERIAL_REASON (ID, REASON_TYPE, NAME, ERP_CODE, SORT_NO, ENABLED) VALUES
        (1, 1, N'良品退料',     'TLYY01_SYS', 1, 1),
        (2, 1, N'来料不良退料', 'TLYY02_SYS', 2, 1);
    SET IDENTITY_INSERT dbo.DB_MATERIAL_REASON OFF;
    PRINT '已重录退料类型字典(reasonType=1)';
END
ELSE
    PRINT '退料类型字典(reasonType=1)已存在，跳过';
GO

PRINT '========== 增量变更20260912：字典改用编码, 主表REASON_ID/RETURN_TYPE转VARCHAR ==========';
-- 背景: 字典REASON_ID改存金蝶编码(如TLYY01_SYS), 而主表REASON_ID/RETURN_TYPE仍为INT,
--       插入报错245(在将varchar值'TLYY01_SYS'转换成数据类型int时失败)

-- 1. 字典表补充 REASON_ID 列(字典编码, 接口3下发/接口5、9回传校验依据; 此前为手工添加, 脚本缺失)
IF COL_LENGTH('dbo.DB_MATERIAL_REASON', 'REASON_ID') IS NULL
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_REASON ADD REASON_ID VARCHAR(50) NULL;
    PRINT '已为 DB_MATERIAL_REASON 增加列 REASON_ID';
END
ELSE
    PRINT '列 DB_MATERIAL_REASON.REASON_ID 已存在，跳过';
GO

-- 2. 字典表 REASON_ID 为定长字符(CHAR/NCHAR)时转VARCHAR并清理尾部空格
--    (CHAR读出带尾部空格, 会原样落库主表: 'TLYY01_SYS______...')
IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID('dbo.DB_MATERIAL_REASON')
             AND name = 'REASON_ID'
             AND system_type_id IN (TYPE_ID('char'), TYPE_ID('nchar')))
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_REASON ALTER COLUMN REASON_ID VARCHAR(50) NULL;
    UPDATE dbo.DB_MATERIAL_REASON SET REASON_ID = RTRIM(REASON_ID) WHERE REASON_ID IS NOT NULL;
    PRINT '已将 DB_MATERIAL_REASON.REASON_ID 转为 VARCHAR 并清理尾部空格';
END
ELSE
    PRINT 'DB_MATERIAL_REASON.REASON_ID 非 CHAR/NCHAR，跳过';
GO

-- 3. 字典回填: reasonType=1(退料类型)的REASON_ID=金蝶FReturnType枚举值(与主键ID一致); reasonType=2由业务维护
IF EXISTS (SELECT 1 FROM dbo.DB_MATERIAL_REASON WHERE REASON_TYPE = 1 AND REASON_ID IS NULL)
BEGIN
    UPDATE dbo.DB_MATERIAL_REASON SET REASON_ID = CAST(ID AS VARCHAR(50))
        WHERE REASON_TYPE = 1 AND REASON_ID IS NULL;
    PRINT '已回填退料类型字典 REASON_ID';
END
ELSE
    PRINT '退料类型字典 REASON_ID 无需回填，跳过';
GO

-- 4. 主表 REASON_ID: INT/CHAR → VARCHAR(50) (存量int值自动转为数字字符串)
IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID('dbo.DB_MATERIAL_CALL')
             AND name = 'REASON_ID'
             AND system_type_id IN (TYPE_ID('int'), TYPE_ID('char'), TYPE_ID('nchar')))
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_CALL ALTER COLUMN REASON_ID VARCHAR(50) NULL;
    UPDATE dbo.DB_MATERIAL_CALL SET REASON_ID = RTRIM(REASON_ID) WHERE REASON_ID IS NOT NULL;
    PRINT '已将 DB_MATERIAL_CALL.REASON_ID 转为 VARCHAR(50)';
END
ELSE
    PRINT 'DB_MATERIAL_CALL.REASON_ID 非 INT/CHAR/NCHAR，跳过';
GO

-- 5. 主表 RETURN_TYPE: INT/CHAR → VARCHAR(50)
IF EXISTS (SELECT 1 FROM sys.columns
           WHERE object_id = OBJECT_ID('dbo.DB_MATERIAL_CALL')
             AND name = 'RETURN_TYPE'
             AND system_type_id IN (TYPE_ID('int'), TYPE_ID('char'), TYPE_ID('nchar')))
BEGIN
    ALTER TABLE dbo.DB_MATERIAL_CALL ALTER COLUMN RETURN_TYPE VARCHAR(50) NULL;
    UPDATE dbo.DB_MATERIAL_CALL SET RETURN_TYPE = RTRIM(RETURN_TYPE) WHERE RETURN_TYPE IS NOT NULL;
    PRINT '已将 DB_MATERIAL_CALL.RETURN_TYPE 转为 VARCHAR(50)';
END
ELSE
    PRINT 'DB_MATERIAL_CALL.RETURN_TYPE 非 INT/CHAR/NCHAR，跳过';
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
