-- 为 mes_dwd_productOrder 表添加 BOMflag 列
-- BOMflag=0 表示未从 ERP 同步备料明细，BOMflag=1 表示已同步到 mes_dwd_material_detail

-- 列已存在则不重复添加（通过 INFORMATION_SCHEMA 判断）
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'mes_dwd_productOrder' AND COLUMN_NAME = 'BOMflag'
)
BEGIN
    ALTER TABLE dbo.mes_dwd_productOrder ADD BOMflag INT NOT NULL DEFAULT 0;
    PRINT '[OK] BOMflag 列已添加，默认值 0';
END
ELSE
BEGIN
    PRINT '[SKIP] BOMflag 列已存在';
END
GO

-- 验证
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'mes_dwd_productOrder' AND COLUMN_NAME = 'BOMflag';
GO
