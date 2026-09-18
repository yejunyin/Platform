-- 为 sys_user 表添加钉钉ID列（质检人员维护页面使用）
-- 列名 dingdingid，存储钉钉用户ID，允许为空（一个人可暂不绑定）

-- 列已存在则不重复添加（通过 INFORMATION_SCHEMA 判断）
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'dingdingid'
)
BEGIN
    ALTER TABLE dbo.sys_user ADD dingdingid VARCHAR(64) NULL;
    PRINT '[OK] dingdingid 列已添加';
END
ELSE
BEGIN
    PRINT '[SKIP] dingdingid 列已存在';
END
GO

-- 验证
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'dingdingid';
GO
