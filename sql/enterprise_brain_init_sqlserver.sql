-- ============================================================
-- 企业大脑(Enterprise Brain) - 数据库初始化脚本
-- 数据库: Microsoft SQL Server 2016+
-- 目标库: HLEIMS2026082008001 (当前在用的 SQL Server 业务库)
-- 排序规则: Chinese_PRC_CI_AS
-- 创建日期: 2026-08-15
-- ============================================================

-- 切换到当前在用的业务库（已存在，无需新建）
USE [HLEIMS2026082008001];
GO

-- ============================================================
-- 1. 系统用户表
-- ============================================================
IF OBJECT_ID(N'sys_user', N'U') IS NOT NULL DROP TABLE [sys_user];
GO
CREATE TABLE [sys_user] (
    [id]              BIGINT          NOT NULL,
    [username]        VARCHAR(64)     NOT NULL,
    [password]        VARCHAR(128)    NOT NULL,
    [real_name]       VARCHAR(64)     NOT NULL,
    [email]           VARCHAR(128)    NULL,
    [phone]           VARCHAR(32)     NULL,
    [avatar]          VARCHAR(512)    NULL,
    [dept_id]         BIGINT          NULL,
    [status]          TINYINT         NOT NULL DEFAULT 1,
    [deleted]         TINYINT         NOT NULL DEFAULT 0,
    [create_by]       BIGINT          NULL,
    [create_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [update_by]       BIGINT          NULL,
    [update_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_sys_user] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UK_sys_user_username] UNIQUE NONCLUSTERED ([username])
);
GO
CREATE NONCLUSTERED INDEX [IX_sys_user_dept_id] ON [sys_user]([dept_id]);
CREATE NONCLUSTERED INDEX [IX_sys_user_status] ON [sys_user]([status]);
GO

-- ============================================================
-- 2. 部门表
-- ============================================================
IF OBJECT_ID(N'sys_dept', N'U') IS NOT NULL DROP TABLE [sys_dept];
GO
CREATE TABLE [sys_dept] (
    [id]              BIGINT          NOT NULL,
    [parent_id]       BIGINT          NOT NULL DEFAULT 0,
    [dept_name]       VARCHAR(64)     NOT NULL,
    [dept_code]       VARCHAR(32)     NOT NULL,
    [sort_order]      INT             NOT NULL DEFAULT 0,
    [leader]          VARCHAR(64)     NULL,
    [phone]           VARCHAR(32)     NULL,
    [status]          TINYINT         NOT NULL DEFAULT 1,
    [deleted]         TINYINT         NOT NULL DEFAULT 0,
    [create_by]       BIGINT          NULL,
    [create_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [update_by]       BIGINT          NULL,
    [update_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_sys_dept] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UK_sys_dept_code] UNIQUE NONCLUSTERED ([dept_code])
);
GO
CREATE NONCLUSTERED INDEX [IX_sys_dept_parent_id] ON [sys_dept]([parent_id]);
GO

-- ============================================================
-- 3. 外部系统配置表
-- ============================================================
IF OBJECT_ID(N'task_external_system', N'U') IS NOT NULL DROP TABLE [task_external_system];
GO
CREATE TABLE [task_external_system] (
    [id]               BIGINT          NOT NULL,
    [system_code]      VARCHAR(32)     NOT NULL,
    [system_name]      VARCHAR(64)     NOT NULL,
    [system_type]      VARCHAR(32)     NOT NULL,
    [base_url]         VARCHAR(256)    NULL,
    [app_key]          VARCHAR(128)    NULL,
    [app_secret]       VARCHAR(256)    NULL,
    [sync_enabled]     TINYINT         NOT NULL DEFAULT 1,
    [sync_cron]        VARCHAR(64)     DEFAULT '0 */5 * * * ?',
    [last_sync_time]   DATETIME2(3)    NULL,
    [status]           TINYINT         NOT NULL DEFAULT 1,
    [deleted]          TINYINT         NOT NULL DEFAULT 0,
    [create_by]        BIGINT          NULL,
    [create_time]      DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [update_by]        BIGINT          NULL,
    [update_time]      DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [remark]           VARCHAR(512)    NULL,
    CONSTRAINT [PK_task_external_system] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UK_task_external_system_code] UNIQUE NONCLUSTERED ([system_code])
);
GO

-- ============================================================
-- 4. 统一任务主表
-- ============================================================
IF OBJECT_ID(N'task_info', N'U') IS NOT NULL DROP TABLE [task_info];
GO
CREATE TABLE [task_info] (
    [id]                  BIGINT          NOT NULL,
    [task_no]             VARCHAR(64)     NOT NULL,
    [external_task_id]    VARCHAR(128)    NULL,
    [external_system]     VARCHAR(32)     NOT NULL,
    [task_type]           VARCHAR(32)     NOT NULL,
    [task_category]       VARCHAR(32)     NULL,
    [priority]            TINYINT         NOT NULL DEFAULT 2,
    [title]               VARCHAR(256)    NOT NULL,
    [content]             NVARCHAR(MAX)   NULL,
    [biz_key]             VARCHAR(128)    NULL,
    [biz_url]             VARCHAR(512)    NULL,
    [initiator_id]        BIGINT          NULL,
    [initiator_name]      VARCHAR(64)     NULL,
    [assignee_id]         BIGINT          NOT NULL,
    [assignee_name]       VARCHAR(64)     NOT NULL,
    [assignee_dept_id]    BIGINT          NULL,
    [assignee_dept_name]  VARCHAR(64)     NULL,
    [cc_user_ids]         VARCHAR(1024)   NULL,
    [task_status]         TINYINT         NOT NULL DEFAULT 0,
    [receive_time]        DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [deadline_time]       DATETIME2(3)    NULL,
    [start_process_time]  DATETIME2(3)    NULL,
    [complete_time]       DATETIME2(3)    NULL,
    [handle_duration]     BIGINT          NULL,
    [is_read]             TINYINT         NOT NULL DEFAULT 0,
    [is_urged]            TINYINT         NOT NULL DEFAULT 0,
    [urge_count]          INT             NOT NULL DEFAULT 0,
    [action_result]       VARCHAR(32)     NULL,
    [action_comment]      VARCHAR(1024)   NULL,
    [sort_weight]         INT             NOT NULL DEFAULT 0,
    [ext_data]            NVARCHAR(MAX)   NULL,
    [deleted]             TINYINT         NOT NULL DEFAULT 0,
    [create_by]           BIGINT          NULL,
    [create_time]         DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [update_by]           BIGINT          NULL,
    [update_time]         DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_task_info] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UK_task_info_task_no] UNIQUE NONCLUSTERED ([task_no]),
    CONSTRAINT [UK_task_info_external] UNIQUE NONCLUSTERED ([external_system], [external_task_id])
);
GO
CREATE NONCLUSTERED INDEX [IX_task_info_assignee_status] ON [task_info]([assignee_id], [task_status]);
CREATE NONCLUSTERED INDEX [IX_task_info_assignee_dept] ON [task_info]([assignee_dept_id], [task_status]);
CREATE NONCLUSTERED INDEX [IX_task_info_external_system] ON [task_info]([external_system]);
CREATE NONCLUSTERED INDEX [IX_task_info_task_type] ON [task_info]([task_type]);
CREATE NONCLUSTERED INDEX [IX_task_info_priority] ON [task_info]([priority]);
CREATE NONCLUSTERED INDEX [IX_task_info_deadline] ON [task_info]([deadline_time]);
CREATE NONCLUSTERED INDEX [IX_task_info_create_time] ON [task_info]([create_time]);
CREATE NONCLUSTERED INDEX [IX_task_info_complete_time] ON [task_info]([complete_time]);
GO

-- ============================================================
-- 5. 任务处理历史表
-- ============================================================
IF OBJECT_ID(N'task_handle_history', N'U') IS NOT NULL DROP TABLE [task_handle_history];
GO
CREATE TABLE [task_handle_history] (
    [id]                  BIGINT          NOT NULL,
    [task_id]             BIGINT          NOT NULL,
    [node_code]           VARCHAR(64)     NULL,
    [node_name]           VARCHAR(128)    NULL,
    [handler_id]          BIGINT          NOT NULL,
    [handler_name]        VARCHAR(64)     NOT NULL,
    [handler_dept_id]     BIGINT          NULL,
    [handler_dept_name]   VARCHAR(64)     NULL,
    [action_type]         VARCHAR(32)     NOT NULL,
    [action_result]       VARCHAR(32)     NULL,
    [action_comment]      VARCHAR(1024)   NULL,
    [previous_handler_id] BIGINT          NULL,
    [next_handler_id]     BIGINT          NULL,
    [attachments]         VARCHAR(1024)   NULL,
    [handle_duration]     BIGINT          NULL,
    [create_time]         DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_task_handle_history] PRIMARY KEY CLUSTERED ([id])
);
GO
CREATE NONCLUSTERED INDEX [IX_handle_history_task_id] ON [task_handle_history]([task_id]);
CREATE NONCLUSTERED INDEX [IX_handle_history_handler_id] ON [task_handle_history]([handler_id]);
CREATE NONCLUSTERED INDEX [IX_handle_history_create_time] ON [task_handle_history]([create_time]);
GO

-- ============================================================
-- 6. 任务转办/委派记录表
-- ============================================================
IF OBJECT_ID(N'task_transfer', N'U') IS NOT NULL DROP TABLE [task_transfer];
GO
CREATE TABLE [task_transfer] (
    [id]                  BIGINT          NOT NULL,
    [task_id]             BIGINT          NOT NULL,
    [transfer_type]       TINYINT         NOT NULL,
    [from_user_id]        BIGINT          NOT NULL,
    [from_user_name]      VARCHAR(64)     NOT NULL,
    [to_user_id]          BIGINT          NOT NULL,
    [to_user_name]        VARCHAR(64)     NOT NULL,
    [reason]              VARCHAR(512)    NULL,
    [transfer_time]       DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    [is_return]           TINYINT         NOT NULL DEFAULT 0,
    [return_time]         DATETIME2(3)    NULL,
    CONSTRAINT [PK_task_transfer] PRIMARY KEY CLUSTERED ([id])
);
GO
CREATE NONCLUSTERED INDEX [IX_transfer_task_id] ON [task_transfer]([task_id]);
CREATE NONCLUSTERED INDEX [IX_transfer_from_user] ON [task_transfer]([from_user_id]);
CREATE NONCLUSTERED INDEX [IX_transfer_to_user] ON [task_transfer]([to_user_id]);
GO

-- ============================================================
-- 7. 操作日志表
-- ============================================================
IF OBJECT_ID(N'sys_operation_log', N'U') IS NOT NULL DROP TABLE [sys_operation_log];
GO
CREATE TABLE [sys_operation_log] (
    [id]              BIGINT          NOT NULL,
    [trace_id]        VARCHAR(64)     NULL,
    [user_id]         BIGINT          NULL,
    [username]        VARCHAR(64)     NULL,
    [module]          VARCHAR(64)     NOT NULL,
    [operation]       VARCHAR(128)    NOT NULL,
    [method]          VARCHAR(16)     NULL,
    [request_url]     VARCHAR(512)    NULL,
    [request_params]  NVARCHAR(MAX)   NULL,
    [response_result] NVARCHAR(MAX)   NULL,
    [ip_address]      VARCHAR(64)     NULL,
    [user_agent]      VARCHAR(512)    NULL,
    [cost_time]       BIGINT          NULL,
    [status]          TINYINT         NOT NULL DEFAULT 1,
    [error_msg]       NVARCHAR(MAX)   NULL,
    [create_time]     DATETIME2(3)    NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT [PK_sys_operation_log] PRIMARY KEY CLUSTERED ([id])
);
GO
CREATE NONCLUSTERED INDEX [IX_oplog_user_id] ON [sys_operation_log]([user_id]);
CREATE NONCLUSTERED INDEX [IX_oplog_module] ON [sys_operation_log]([module]);
CREATE NONCLUSTERED INDEX [IX_oplog_create_time] ON [sys_operation_log]([create_time]);
GO

-- ============================================================
-- ============ 初始化数据 ============
-- ============================================================

SET NOCOUNT ON;
GO

-- 初始化部门
INSERT INTO [sys_dept] ([id],[parent_id],[dept_name],[dept_code],[sort_order],[leader],[status],[deleted]) VALUES
(1, 0, N'集团总部',   'HQ', 0, N'张总',    1, 0),
(2, 1, N'技术研发部', 'RD', 1, N'李总监',  1, 0),
(3, 1, N'质量管理部', 'QA', 2, N'王总监',  1, 0),
(4, 1, N'生产制造部', 'PD', 3, N'赵总监',  1, 0),
(5, 1, N'采购供应链部','SC',4, N'刘总监',  1, 0),
(6, 1, N'人力资源部', 'HR', 5, N'陈总监',  1, 0),
(7, 1, N'财务管理部', 'FI', 6, N'周总监',  1, 0);
GO

-- 初始化用户 (密码: 123456 - BCrypt加密)
INSERT INTO [sys_user] ([id],[username],[password],[real_name],[email],[phone],[dept_id],[status],[deleted]) VALUES
(1,'admin',  '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', N'系统管理员', 'admin@eb.com', '13800000000', 1, 1, 0),
(2,'zhangsan','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', N'张三',     'zhangsan@eb.com','13800000001', 3, 1, 0),
(3,'lisi',   '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', N'李四',     'lisi@eb.com',    '13800000002', 4, 1, 0),
(4,'wangwu', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', N'王五',     'wangwu@eb.com',  '13800000003', 5, 1, 0),
(5,'zhaoliu','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', N'赵六',     'zhaoliu@eb.com', '13800000004', 2, 1, 0);
GO

-- 初始化外部系统
INSERT INTO [task_external_system] ([id],[system_code],[system_name],[system_type],[base_url],[sync_enabled],[sync_cron],[status],[deleted],[remark]) VALUES
(1, 'OA',  N'协同办公系统',       'REST', 'http://oa.company.com/api',   1, '0 */5 * * * ?', 1, 0, N'泛微OA系统对接'),
(2, 'ERP', N'企业资源计划系统',   'REST', 'http://erp.company.com/api',  1, '0 */3 * * * ?', 1, 0, N'SAP ERP系统对接'),
(3, 'MES', N'制造执行系统',       'MQ',   NULL,                           1, '0 */2 * * * ?', 1, 0, N'MES生产任务同步'),
(4, 'EB',  N'企业大脑平台',       'REST', NULL,                           1, NULL,            1, 0, N'平台自建任务');
GO

-- 初始化示例任务
INSERT INTO [task_info] ([id],[task_no],[external_system],[external_task_id],[task_type],[task_category],[priority],[title],[content],
    [biz_key],[initiator_id],[initiator_name],[assignee_id],[assignee_name],[assignee_dept_id],[assignee_dept_name],
    [task_status],[receive_time],[deadline_time],[sort_weight],[is_read],[is_urged],[urge_count],[deleted]) VALUES
(1001,'TK202608150001','OA','OA-2026-00881','APPROVAL','LEAVE',2,N'【审批】张三-年假申请-5天',
    N'申请日期：2026-08-20 至 2026-08-24，共5天年假，请审批。',
    'LV-2026-0815-001', 2, N'张三', 1, N'系统管理员', 1, N'集团总部', 0,
    DATEADD(HOUR,  9, CAST('2026-08-15' AS DATETIME2(3))),
    DATEADD(HOUR, 18, CAST('2026-08-16' AS DATETIME2(3))),
    100, 0, 0, 0, 0),
(1002,'TK202608150002','ERP','ERP-PO-20260815001','APPROVAL','PURCHASE',1,N'【紧急审批】原材料采购订单审批-¥580,000',
    N'采购供应商：XX材料科技有限公司，采购A类原材料10吨，单价58,000元/吨，合计580,000元。生产紧急需求，请尽快审批。',
    'PO-20260815-001', 4, N'王五', 1, N'系统管理员', 1, N'集团总部', 0,
    DATEADD(HOUR, 10, DATEADD(MINUTE, 30, CAST('2026-08-15' AS DATETIME2(3)))),
    DATEADD(HOUR, 18, CAST('2026-08-15' AS DATETIME2(3))),
    200, 0, 0, 0, 0),
(1003,'TK202608150003','MES','MES-WO-2026-0815-012','TODO','PRODUCTION',3,N'【待办】生产工单执行-A产品-500件',
    N'生产工单：WO-2026-0815-012，产品：A型号产品，数量：500件，计划完成日期：2026-08-20',
    'WO-2026-0815-012', 3, N'李四', 3, N'李四', 4, N'生产制造部', 1,
    DATEADD(HOUR, 8, CAST('2026-08-15' AS DATETIME2(3))),
    DATEADD(HOUR, 18, CAST('2026-08-20' AS DATETIME2(3))),
    50, 0, 0, 0, 0),
(1004,'TK202608150004','EB',NULL,'APPROVAL','QUALITY',2,N'【审批】不合格品处理申请-NCR20260815001',
    N'质量异常报告：IQC来料检验发现批次B2026081401的电子元件不合格，申请退货处理，涉及金额¥35,000。',
    'NCR-20260815-001', 2, N'张三', 1, N'系统管理员', 1, N'集团总部', 0,
    DATEADD(HOUR, 14, CAST('2026-08-15' AS DATETIME2(3))),
    DATEADD(HOUR, 18, CAST('2026-08-17' AS DATETIME2(3))),
    80, 0, 0, 0, 0),
(1005,'TK202608150005','OA','OA-2026-00876','APPROVAL','EXPENSE',3,N'【审批】李四-差旅费报销-¥3,280',
    N'报销明细：上海出差往返机票¥1,800，酒店住宿¥1,200，餐饮¥280，合计¥3,280。',
    'EX-20260814-003', 3, N'李四', 1, N'系统管理员', 1, N'集团总部', 2,
    DATEADD(HOUR, 16, CAST('2026-08-14' AS DATETIME2(3))),
    NULL, 0, 0, 0, 0, 0),
(1006,'TK202608150006','ERP','ERP-PO-20260810005','APPROVAL','PURCHASE',2,N'【审批】办公用品采购审批-¥12,500',
    N'采购内容：办公电脑5台、打印耗材一批，合计¥12,500。',
    'PO-20260810-005', 4, N'王五', 1, N'系统管理员', 1, N'集团总部', 2,
    DATEADD(HOUR, 11, CAST('2026-08-10' AS DATETIME2(3))),
    NULL, 0, 0, 0, 0, 0);
GO

UPDATE [task_info] SET
    [start_process_time] = DATEADD(HOUR, 17, CAST('2026-08-14' AS DATETIME2(3))),
    [complete_time]      = DATEADD(HOUR, 17, DATEADD(MINUTE, 30, CAST('2026-08-14' AS DATETIME2(3)))),
    [handle_duration]    = 1800,
    [action_result]      = 'AGREE',
    [action_comment]     = N'同意报销，票据齐全。'
WHERE [id] = 1005;
GO
UPDATE [task_info] SET
    [start_process_time] = DATEADD(HOUR, 14, CAST('2026-08-10' AS DATETIME2(3))),
    [complete_time]      = DATEADD(HOUR, 15, DATEADD(MINUTE, 20, CAST('2026-08-10' AS DATETIME2(3)))),
    [handle_duration]    = 4800,
    [action_result]      = 'AGREE',
    [action_comment]     = N'同意采购。'
WHERE [id] = 1006;
GO

-- 任务处理历史
INSERT INTO [task_handle_history] ([id],[task_id],[node_code],[node_name],[handler_id],[handler_name],[handler_dept_id],[handler_dept_name],
    [action_type],[action_result],[action_comment],[create_time]) VALUES
(1, 1001, 'START',   N'发起申请',   2, N'张三', 3, N'质量管理部',     'CREATE',  NULL,    N'提交年假申请',                    DATEADD(HOUR, 9,  CAST('2026-08-15' AS DATETIME2(3)))),
(2, 1002, 'START',   N'创建订单',   4, N'王五', 5, N'采购供应链部',   'CREATE',  NULL,    N'创建紧急采购订单',                DATEADD(HOUR, 10, DATEADD(MINUTE, 30, CAST('2026-08-15' AS DATETIME2(3))))),
(3, 1003, 'START',   N'下发工单',   3, N'李四', 4, N'生产制造部',     'CREATE',  NULL,    N'生产工单下发',                    DATEADD(HOUR, 8,  CAST('2026-08-15' AS DATETIME2(3)))),
(4, 1003, 'EXEC',    N'执行中',     3, N'李四', 4, N'生产制造部',     'CLAIM',   NULL,    N'领取工单开始生产',                DATEADD(HOUR, 8,  DATEADD(MINUTE, 30, CAST('2026-08-15' AS DATETIME2(3))))),
(5, 1004, 'START',   N'提交NCR',    2, N'张三', 3, N'质量管理部',     'CREATE',  NULL,    N'提交不合格品处理单',              DATEADD(HOUR, 14, CAST('2026-08-15' AS DATETIME2(3)))),
(6, 1005, 'START',   N'提交报销',   3, N'李四', 4, N'生产制造部',     'CREATE',  NULL,    N'提交差旅费报销',                  DATEADD(HOUR, 16, CAST('2026-08-14' AS DATETIME2(3)))),
(7, 1005, 'MANAGER', N'主管审批',   1, N'系统管理员', 1, N'集团总部', 'APPROVE', 'AGREE', N'同意报销，票据齐全。',            DATEADD(HOUR, 17, DATEADD(MINUTE, 30, CAST('2026-08-14' AS DATETIME2(3))))),
(8, 1006, 'START',   N'提交采购',   4, N'王五', 5, N'采购供应链部',   'CREATE',  NULL,    N'提交办公用品采购申请',            DATEADD(HOUR, 11, CAST('2026-08-10' AS DATETIME2(3)))),
(9, 1006, 'MANAGER', N'主管审批',   1, N'系统管理员', 1, N'集团总部', 'APPROVE', 'AGREE', N'同意采购。',                      DATEADD(HOUR, 15, DATEADD(MINUTE, 20, CAST('2026-08-10' AS DATETIME2(3)))));
GO
