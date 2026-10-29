-- ============================================================
-- 工资核算模块：业务状态/组别/时间 从主表 wage_order 下沉到子表 wage_material
-- 数据库: HLEIMS2026082008001 (SQL Server 2016+)
-- 变更原因：一个生产任务单可能对应多条丝印/移印物料，每条明细
--           各自有加工组别、开工时间、完工时间、结算状态等
-- 幂等：可重复执行
-- ============================================================

USE [HLEIMS2026082008001];
GO

-- ------------------------------------------------------------
-- Step 1: wage_material 新增列（如果不存在）
-- ------------------------------------------------------------
IF COL_LENGTH('wage_material', 'process_name') IS NULL
    ALTER TABLE [wage_material] ADD [process_name] NVARCHAR(20) NULL;
ELSE PRINT '[SKIP] process_name 已存在';
GO

IF COL_LENGTH('wage_material', 'unit_price') IS NULL
    ALTER TABLE [wage_material] ADD [unit_price] DECIMAL(14,4) NOT NULL DEFAULT 0;
ELSE PRINT '[SKIP] unit_price 已存在';
GO

IF COL_LENGTH('wage_material', 'group_id') IS NULL
    ALTER TABLE [wage_material] ADD [group_id] BIGINT NULL;
ELSE PRINT '[SKIP] group_id 已存在';
GO

IF COL_LENGTH('wage_material', 'start_time') IS NULL
    ALTER TABLE [wage_material] ADD [start_time] DATETIME2 NULL;
ELSE PRINT '[SKIP] start_time 已存在';
GO

IF COL_LENGTH('wage_material', 'finish_user') IS NULL
    ALTER TABLE [wage_material] ADD [finish_user] NVARCHAR(64) NULL;
ELSE PRINT '[SKIP] finish_user 已存在';
GO

IF COL_LENGTH('wage_material', 'finish_qty') IS NULL
    ALTER TABLE [wage_material] ADD [finish_qty] DECIMAL(18,4) NULL;
ELSE PRINT '[SKIP] finish_qty 已存在';
GO

IF COL_LENGTH('wage_material', 'finish_time') IS NULL
    ALTER TABLE [wage_material] ADD [finish_time] DATETIME2 NULL;
ELSE PRINT '[SKIP] finish_time 已存在';
GO

IF COL_LENGTH('wage_material', 'stock_in_qty') IS NULL
    ALTER TABLE [wage_material] ADD [stock_in_qty] DECIMAL(18,4) NULL;
ELSE PRINT '[SKIP] stock_in_qty 已存在';
GO

IF COL_LENGTH('wage_material', 'settle_status') IS NULL
    ALTER TABLE [wage_material] ADD [settle_status] NVARCHAR(8) NOT NULL DEFAULT N'待结算';
ELSE PRINT '[SKIP] settle_status 已存在';
GO

PRINT '[OK] Step 1: wage_material 新列准备完成';
GO

-- ------------------------------------------------------------
-- Step 2: 从 material_name 解析回填 process_name
-- ------------------------------------------------------------
UPDATE [wage_material]
SET [process_name] = CASE
    WHEN [material_name] LIKE N'%丝印%' THEN N'丝印'
    WHEN [material_name] LIKE N'%移印%' THEN N'移印'
    ELSE [process_name]
END
WHERE [process_name] IS NULL AND [deleted] = 0;
PRINT '[OK] Step 2: process_name 历史数据回填';
GO

-- ------------------------------------------------------------
-- Step 3: 从 wage_order 按 task_no 回填历史状态到 wage_material
--         （只回填 wage_material 自身字段为 NULL 的）
-- ------------------------------------------------------------
UPDATE m SET
    m.[group_id]      = o.[group_id],
    m.[start_time]    = o.[start_time],
    m.[finish_user]   = o.[finish_user],
    m.[finish_qty]    = o.[finish_qty],
    m.[finish_time]   = o.[finish_time],
    m.[stock_in_qty]  = o.[stock_in_qty],
    m.[settle_status] = o.[settle_status],
    m.[biz_status]    = o.[biz_status]
FROM [wage_material] m
INNER JOIN [wage_order] o ON o.task_no = m.task_no AND o.deleted = 0
WHERE m.deleted = 0;
PRINT '[OK] Step 3: 从 wage_order 回填状态到 wage_material 完成';
GO

-- ------------------------------------------------------------
-- Step 4: 索引
-- ------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_material_process' AND object_id = OBJECT_ID('wage_material'))
    CREATE NONCLUSTERED INDEX [IX_wage_material_process] ON [wage_material]([process_name]);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_material_group' AND object_id = OBJECT_ID('wage_material'))
    CREATE NONCLUSTERED INDEX [IX_wage_material_group] ON [wage_material]([group_id]);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_material_biz' AND object_id = OBJECT_ID('wage_material'))
    CREATE NONCLUSTERED INDEX [IX_wage_material_biz] ON [wage_material]([biz_status], [start_time]);
GO

PRINT '[DONE] 迁移脚本执行完成';
GO
