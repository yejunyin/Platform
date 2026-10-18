-- ============================================================
-- 工资辅助核算（丝印/移印计件工资）建表脚本
-- 数据库: HLEIMS2026082008001 (SQL Server 2016+)
-- 说明:
--   1. 员工身份复用 sys_user（username=工号, real_name=姓名, status=在职状态）
--      工资专属属性（工种/系数/所属小组/入职日期）放 wage_staff_ext (1:1)
--   2. 全部字段使用见名知意的 snake_case 命名，不使用随机字段 id
--   3. 逻辑删除 deleted TINYINT (0正常 1删除)，配合 MyBatis-Plus 全局配置
-- 幂等：可重复执行（IF NOT EXISTS 判断）
-- ============================================================

USE [HLEIMS2026082008001];
GO

-- ------------------------------------------------------------
-- 1. 人员小组 wage_group
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_group', N'U') IS NULL
CREATE TABLE [wage_group] (
    [id]              BIGINT          NOT NULL,
    [group_name]      NVARCHAR(50)    NOT NULL,          -- 小组名称（丝印一组等）
    [leader_no]       VARCHAR(64)     NULL,              -- 组长工号
    [default_process] NVARCHAR(20)    NULL,              -- 默认工序（丝印/移印）
    [coef_sum]        DECIMAL(10,2)   NOT NULL DEFAULT 0,-- 组内在职成员系数和（冗余自动回写）
    [member_count]    INT             NOT NULL DEFAULT 0,-- 组内在职成员数（冗余自动回写）
    [remark]          NVARCHAR(200)   NULL,
    [deleted]         TINYINT         NOT NULL DEFAULT 0,
    [create_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [update_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_group] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UK_wage_group_name' AND object_id = OBJECT_ID('wage_group'))
    CREATE UNIQUE NONCLUSTERED INDEX [UK_wage_group_name] ON [wage_group]([group_name]) WHERE [deleted] = 0;
GO

-- ------------------------------------------------------------
-- 2. 员工工资扩展资料 wage_staff_ext（1:1 关联 sys_user.id）
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_staff_ext', N'U') IS NULL
CREATE TABLE [wage_staff_ext] (
    [id]           BIGINT         NOT NULL,
    [user_id]      BIGINT         NOT NULL,               -- sys_user.id
    [job_type]     NVARCHAR(10)   NOT NULL DEFAULT N'员工',-- 工种：员工/组长/班长
    [group_id]     BIGINT         NULL,                   -- 所属小组 wage_group.id
    [coef]         DECIMAL(6,2)   NOT NULL DEFAULT 1.00,  -- 人员系数（工资分摊用）
    [hire_date]    DATE           NULL,                   -- 入职日期
    [deleted]      TINYINT        NOT NULL DEFAULT 0,
    [create_time]  DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]  DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_staff_ext] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UK_wage_staff_ext_user' AND object_id = OBJECT_ID('wage_staff_ext'))
    CREATE UNIQUE NONCLUSTERED INDEX [UK_wage_staff_ext_user] ON [wage_staff_ext]([user_id]) WHERE [deleted] = 0;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_staff_ext_group' AND object_id = OBJECT_ID('wage_staff_ext'))
    CREATE NONCLUSTERED INDEX [IX_wage_staff_ext_group] ON [wage_staff_ext]([group_id]);
GO

-- ------------------------------------------------------------
-- 3. 工序基础资料 wage_process
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_process', N'U') IS NULL
CREATE TABLE [wage_process] (
    [id]           BIGINT         NOT NULL,
    [process_code] VARCHAR(32)    NOT NULL,               -- 工序编号
    [process_name] NVARCHAR(50)   NOT NULL,               -- 工序名称（单价资料下拉来源）
    [workshop]     NVARCHAR(50)   NULL,                   -- 所属车间
    [remark]       NVARCHAR(200)  NULL,
    [deleted]      TINYINT        NOT NULL DEFAULT 0,
    [create_time]  DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]  DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_process] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UK_wage_process_code' AND object_id = OBJECT_ID('wage_process'))
    CREATE UNIQUE NONCLUSTERED INDEX [UK_wage_process_code] ON [wage_process]([process_code]) WHERE [deleted] = 0;
GO

-- ------------------------------------------------------------
-- 4. 单价工序资料 wage_price（子物料 × 工序 的计件单价）
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_price', N'U') IS NULL
CREATE TABLE [wage_price] (
    [id]             BIGINT         NOT NULL,
    [material_code]  VARCHAR(64)    NOT NULL,             -- 子物料代码
    [material_name]  NVARCHAR(200)  NULL,                 -- 子项物料名称
    [process_name]   NVARCHAR(50)   NOT NULL,             -- 工序名称
    [unit_price]     DECIMAL(14,4)  NOT NULL DEFAULT 0,   -- 单价（元）
    [effective_date] DATE           NULL,                 -- 生效日期
    [expiry_date]    DATE           NULL,                 -- 失效日期（NULL=长期有效）
    [status]         NVARCHAR(4)    NOT NULL DEFAULT N'启用',-- 启用/停用
    [deleted]        TINYINT        NOT NULL DEFAULT 0,
    [create_time]    DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]    DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_price] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_price_mat' AND object_id = OBJECT_ID('wage_price'))
    CREATE NONCLUSTERED INDEX [IX_wage_price_mat] ON [wage_price]([material_code]);
GO

-- ------------------------------------------------------------
-- 5. 生产任务单 wage_order（工资核算口径的任务单，MES/金蝶同步 + 核算状态）
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_order', N'U') IS NULL
CREATE TABLE [wage_order] (
    [id]              BIGINT         NOT NULL,
    [task_no]         VARCHAR(64)    NOT NULL,            -- 生产任务单号（=MES pcode）
    [feed_no]         VARCHAR(64)    NULL,                -- 投料单号
    [product_name]    NVARCHAR(200)  NULL,                -- 产品名称
    [sub_material_code] VARCHAR(64)  NULL,                -- 子物料代码（单价匹配键）
    [sub_material_name] NVARCHAR(200) NULL,               -- 子项物料名称
    [product_qty]     DECIMAL(18,4)  NULL,                -- 产品数量
    [process_name]    NVARCHAR(20)   NULL,                -- 工序名称（丝印/移印）
    [workshop]        NVARCHAR(50)   NULL,                -- 生产车间
    [unit_price]      DECIMAL(14,4)  NOT NULL DEFAULT 0,  -- 单价（冗余，同步时按字典带出）
    [group_id]        BIGINT         NULL,                -- 加工组别 wage_group.id
    [confirm_status]  NVARCHAR(8)    NOT NULL DEFAULT N'未确认', -- 未确认/已确认
    [biz_status]      NVARCHAR(8)    NOT NULL DEFAULT N'计划',   -- 业务状态 计划/开工/完工
    [start_time]      DATETIME2      NULL,                -- 开工时间
    [finish_user]     NVARCHAR(64)   NULL,                -- 完工确认人
    [finish_qty]      DECIMAL(18,4)  NULL,                -- 完工数量
    [finish_time]     DATETIME2      NULL,                -- 完工时间
    [stock_in_qty]    DECIMAL(18,4)  NULL,                -- 完工入库数（金蝶回写，NULL/0 显示 —）
    [settle_status]   NVARCHAR(8)    NOT NULL DEFAULT N'待结算', -- 待结算/已提交/已结算
    [sync_time]       DATETIME2      NULL,                -- 最近同步时间
    [deleted]         TINYINT        NOT NULL DEFAULT 0,
    [create_time]     DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]     DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_order] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UK_wage_order_task_no' AND object_id = OBJECT_ID('wage_order'))
    CREATE UNIQUE NONCLUSTERED INDEX [UK_wage_order_task_no] ON [wage_order]([task_no]) WHERE [deleted] = 0;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_order_group' AND object_id = OBJECT_ID('wage_order'))
    CREATE NONCLUSTERED INDEX [IX_wage_order_group] ON [wage_order]([group_id]);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_order_biz' AND object_id = OBJECT_ID('wage_order'))
    CREATE NONCLUSTERED INDEX [IX_wage_order_biz] ON [wage_order]([biz_status], [start_time]);
GO

-- ------------------------------------------------------------
-- 6. 生产用料清单 wage_material（开工提交时由金蝶《生产用料清单》下发）
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_material', N'U') IS NULL
CREATE TABLE [wage_material] (
    [id]            BIGINT         NOT NULL,
    [task_no]       VARCHAR(64)    NOT NULL,              -- 生产任务单号
    [feed_no]       VARCHAR(64)    NULL,                  -- 投料单号
    [biz_status]    NVARCHAR(8)    NOT NULL DEFAULT N'计划', -- 随任务单联动 计划/开工/完工
    [material_code] VARCHAR(64)    NOT NULL,              -- 物料代码
    [material_name] NVARCHAR(200)  NULL,                  -- 物料名称
    [unit]          NVARCHAR(20)   NULL,                  -- 单位
    [qty]           DECIMAL(18,4)  NOT NULL DEFAULT 0,    -- 数量
    [deleted]       TINYINT        NOT NULL DEFAULT 0,
    [create_time]   DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]   DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_material] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_material_task' AND object_id = OBJECT_ID('wage_material'))
    CREATE NONCLUSTERED INDEX [IX_wage_material_task] ON [wage_material]([task_no], [material_code]);
GO

-- ------------------------------------------------------------
-- 7. 开工确认记录 wage_confirm（开工提交时写入留痕）
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_confirm', N'U') IS NULL
CREATE TABLE [wage_confirm] (
    [id]               BIGINT         NOT NULL,
    [task_no]          VARCHAR(64)    NOT NULL,
    [sub_material_code] VARCHAR(64)   NULL,
    [sub_material_name] NVARCHAR(200) NULL,
    [group_id]         BIGINT         NULL,
    [process_name]     NVARCHAR(20)   NULL,
    [confirm_qty]      DECIMAL(18,4)  NULL,               -- 确认数量
    [erp_qty]          DECIMAL(18,4)  NULL,               -- ERP数量
    [confirmer_no]     VARCHAR(64)    NULL,               -- 确认人工号
    [confirm_time]     DATETIME2      NULL,
    [confirm_status]   NVARCHAR(8)    NOT NULL DEFAULT N'已确认', -- 已确认/已取消
    [remark]           NVARCHAR(200)  NULL,
    [deleted]          TINYINT        NOT NULL DEFAULT 0,
    [create_time]      DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]      DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_confirm] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_confirm_task' AND object_id = OBJECT_ID('wage_confirm'))
    CREATE NONCLUSTERED INDEX [IX_wage_confirm_task] ON [wage_confirm]([task_no]);
GO

-- ------------------------------------------------------------
-- 8. 工资统计明细（计件复核）wage_pay_stat
--    核算单元 = 生产任务单 × 用料明细 × 成员，由系统按开工月份自动生成，可复核
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_pay_stat', N'U') IS NULL
CREATE TABLE [wage_pay_stat] (
    [id]                 BIGINT         NOT NULL,
    [wage_month]         CHAR(7)        NOT NULL,         -- 工资月份 YYYY-MM（按开工时间归集）
    [task_no]            VARCHAR(64)    NOT NULL,         -- 生产任务单号
    [group_id]           BIGINT         NULL,
    [group_name]         NVARCHAR(50)   NULL,             -- 小组（冗余）
    [staff_name]         NVARCHAR(64)   NOT NULL,         -- 员工姓名
    [staff_no]           VARCHAR(64)    NOT NULL,         -- 工号
    [job_type]           NVARCHAR(10)   NULL,             -- 工种
    [product_name]       NVARCHAR(200)  NULL,             -- 品名
    [unit]               NVARCHAR(20)   NULL,             -- 单位
    [work_price]         DECIMAL(14,4)  NULL,             -- 工价（=子项单价）
    [qty]                DECIMAL(18,4)  NULL,             -- 数量（=子项数量）
    [amount]             DECIMAL(14,2)  NULL,             -- 个人金额 = 工单组级金额 × 分摊比例（可人工覆盖）
    [start_time]         DATETIME2      NULL,             -- 开工时间（月份归属依据）
    [process_name]       NVARCHAR(20)   NULL,             -- 工序
    [sub_material_code]  VARCHAR(64)    NULL,             -- 子物料代码
    [sub_material_name]  NVARCHAR(200)  NULL,             -- 子项物料名称
    [sub_qty]            DECIMAL(18,4)  NULL,             -- 子项数量
    [sub_price]          DECIMAL(14,4)  NULL,             -- 子项单价
    [detail_amount]      DECIMAL(14,2)  NULL,             -- 明细金额 = 子项数量 × 子项单价
    [group_amount]       DECIMAL(14,2)  NULL,             -- 工单组级金额 = 该工单全部明细金额之和（分摊基数）
    [coef]               DECIMAL(6,2)   NULL,             -- 人员系数
    [share_ratio]        DECIMAL(8,4)   NULL,             -- 分摊比例 = 个人系数 ÷ 组内系数和
    [reviewer]           NVARCHAR(64)   NULL,             -- 复核人（当前登录账户，只读）
    [review_time]        DATETIME2      NULL,             -- 复核时间
    [entry_user]         NVARCHAR(64)   NULL,             -- 录入/生成操作人
    [remark]             NVARCHAR(200)  NULL,
    [deleted]            TINYINT        NOT NULL DEFAULT 0,
    [create_time]        DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]        DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_pay_stat] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_pay_stat_month' AND object_id = OBJECT_ID('wage_pay_stat'))
    CREATE NONCLUSTERED INDEX [IX_wage_pay_stat_month] ON [wage_pay_stat]([wage_month]);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_pay_stat_staff' AND object_id = OBJECT_ID('wage_pay_stat'))
    CREATE NONCLUSTERED INDEX [IX_wage_pay_stat_staff] ON [wage_pay_stat]([staff_no], [wage_month]);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_pay_stat_task' AND object_id = OBJECT_ID('wage_pay_stat'))
    CREATE NONCLUSTERED INDEX [IX_wage_pay_stat_task] ON [wage_pay_stat]([task_no]);
GO

-- ------------------------------------------------------------
-- 9. 工资表（个人月度工资，审核流转）wage_record
--    工资单号规则 WG{YYYYMM}-{工号}，审核时按需生成
-- ------------------------------------------------------------
IF OBJECT_ID(N'wage_record', N'U') IS NULL
CREATE TABLE [wage_record] (
    [id]            BIGINT         NOT NULL,
    [wage_no]       VARCHAR(40)    NOT NULL,              -- 工资单号 WG{YYYYMM}-{工号}
    [staff_name]    NVARCHAR(64)   NULL,
    [staff_no]      VARCHAR(64)    NOT NULL,
    [group_id]      BIGINT         NULL,
    [group_name]    NVARCHAR(50)   NULL,
    [coef]          DECIMAL(6,2)   NULL,
    [settle_period] CHAR(7)        NOT NULL,              -- 结算周期 YYYY-MM
    [piece_amount]  DECIMAL(14,2)  NOT NULL DEFAULT 0,    -- 计件金额
    [audit_status]  NVARCHAR(8)    NOT NULL DEFAULT N'待审核', -- 待审核/已审核/已驳回
    [auditor]       NVARCHAR(64)   NULL,                  -- 审核人
    [settle_date]   DATE           NULL,                  -- 结算/审核日期
    [remark]        NVARCHAR(200)  NULL,
    [deleted]       TINYINT        NOT NULL DEFAULT 0,
    [create_time]   DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    [update_time]   DATETIME2(3)   NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_wage_record] PRIMARY KEY CLUSTERED ([id])
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UK_wage_record_no' AND object_id = OBJECT_ID('wage_record'))
    CREATE UNIQUE NONCLUSTERED INDEX [UK_wage_record_no] ON [wage_record]([wage_no]) WHERE [deleted] = 0;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_wage_record_period' AND object_id = OBJECT_ID('wage_record'))
    CREATE NONCLUSTERED INDEX [IX_wage_record_period] ON [wage_record]([settle_period], [staff_no]);
GO

PRINT '[OK] 工资核算 9 张表创建/校验完成';
GO
