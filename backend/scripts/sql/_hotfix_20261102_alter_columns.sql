-- ============================================================
-- Hotfix 2026-11-02: 扩长 bill_no / task_no / product_name 列防截断
-- 目标库: HLEIMS2026082008001
-- 原因: generateNextBillNo 生成 {productionNo}-{MMddHHmmssSSS} 超过原 VARCHAR(32)
-- 用法: 在 SSMS 连接目标库后整段执行
-- ============================================================

USE HLEIMS2026082008001
GO

PRINT '===== 1. wms_picking 表扩长 =====';
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.wms_picking') AND name = 'bill_no')
BEGIN
    ALTER TABLE dbo.wms_picking ALTER COLUMN bill_no VARCHAR(100) NOT NULL;
    PRINT '  [OK] wms_picking.bill_no → VARCHAR(100)';
END
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.wms_picking') AND name = 'product_name')
BEGIN
    ALTER TABLE dbo.wms_picking ALTER COLUMN product_name NVARCHAR(500) NULL;
    PRINT '  [OK] wms_picking.product_name → NVARCHAR(500)';
END
GO

PRINT '===== 2. wms_pick_task 表扩长 =====';
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.wms_pick_task') AND name = 'task_no')
BEGIN
    ALTER TABLE dbo.wms_pick_task ALTER COLUMN task_no VARCHAR(64) NOT NULL;
    PRINT '  [OK] wms_pick_task.task_no → VARCHAR(64)';
END
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.wms_pick_task') AND name = 'product_name')
BEGIN
    ALTER TABLE dbo.wms_pick_task ALTER COLUMN product_name NVARCHAR(500) NULL;
    PRINT '  [OK] wms_pick_task.product_name → NVARCHAR(500)';
END
GO

PRINT '===== 3. wms_pick_task_log 表扩长 =====';
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.wms_pick_task_log') AND name = 'bill_no')
BEGIN
    ALTER TABLE dbo.wms_pick_task_log ALTER COLUMN bill_no VARCHAR(100) NOT NULL;
    PRINT '  [OK] wms_pick_task_log.bill_no → VARCHAR(100)';
END
GO

PRINT '===== 4. 补插刚才被截断的记录（可选） =====';
-- 2026-11-02 13:40:51 push 接口因 bill_no VARCHAR(32) 截断失败
-- WMS 侧其实已经收到了，但本地没存。下面是从日志参数重建的 INSERT。
-- 如果不想补（让前端重新推一遍即可），把这段注释掉就行。

IF NOT EXISTS (SELECT 1 FROM dbo.wms_picking WHERE id = 2117129791899955201)
BEGIN
    INSERT INTO dbo.wms_picking (
        id, bill_no, production_no, product_code, product_name,
        line_code, org_code, dept_code, status, message,
        request_id, wms_task_no, send_time, create_time, update_time, deleted
    ) VALUES (
        2117129791899955201,
        N'WORK26091438954A-002-1102134040918',
        N'WORK26091438954A-002',
        N'1550455001',
        N'1030C角磨#100MM#220V#美式极性插#INGCO#AG70012#(',
        N'LB-CX-04',
        N'102',
        N'1022016',
        N'sent',
        N'领料申请接收成功',
        N'7fdff26a-2490-4d7a-ada2-3cd27f584cde',
        NULL,
        '2026-11-02T13:40:51.411159',
        '2026-11-02T13:40:51.411159',
        '2026-11-02T13:40:51.411159',
        0
    );
    PRINT '  [OK] 补插 wms_picking id=2117129791899955201';
END
ELSE
BEGIN
    PRINT '  [SKIP] 该记录已存在';
END
GO

PRINT '===== 验证 =====';
SELECT 'wms_picking' AS tbl, COUNT(*) AS cnt FROM dbo.wms_picking
UNION ALL
SELECT 'wms_pick_task', COUNT(*) FROM dbo.wms_pick_task
UNION ALL
SELECT 'wms_pick_task_log', COUNT(*) FROM dbo.wms_pick_task_log;

-- 检查刚才补的那条
SELECT id, bill_no, production_no, status, create_time FROM dbo.wms_picking WHERE id = 2117129791899955201;
GO
PRINT '完成。';
