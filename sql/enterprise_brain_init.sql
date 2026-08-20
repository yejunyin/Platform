-- ============================================================
-- 企业大脑(Enterprise Brain) - 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- 字符集: utf8mb4
-- 创建日期: 2026-08-15
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS enterprise_brain
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE enterprise_brain;

-- ============================================================
-- 1. 系统用户表 - 基础用户信息
-- ============================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL COMMENT '用户ID',
    username        VARCHAR(64)     NOT NULL COMMENT '登录账号',
    password        VARCHAR(128)    NOT NULL COMMENT '密码(BCrypt加密)',
    real_name       VARCHAR(64)     NOT NULL COMMENT '真实姓名',
    email           VARCHAR(128)    DEFAULT NULL COMMENT '邮箱',
    phone           VARCHAR(32)     DEFAULT NULL COMMENT '手机号',
    avatar          VARCHAR(512)    DEFAULT NULL COMMENT '头像URL',
    dept_id         BIGINT          DEFAULT NULL COMMENT '部门ID',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '删除标志: 0-未删除, 1-已删除',
    create_by       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_dept_id (dept_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ============================================================
-- 2. 部门表
-- ============================================================
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
    id              BIGINT          NOT NULL COMMENT '部门ID',
    parent_id       BIGINT          NOT NULL DEFAULT 0 COMMENT '父部门ID, 0为顶级部门',
    dept_name       VARCHAR(64)     NOT NULL COMMENT '部门名称',
    dept_code       VARCHAR(32)     NOT NULL COMMENT '部门编码',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    leader          VARCHAR(64)     DEFAULT NULL COMMENT '负责人',
    phone           VARCHAR(32)     DEFAULT NULL COMMENT '联系电话',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '删除标志: 0-未删除, 1-已删除',
    create_by       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_code (dept_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- ============================================================
-- 3. 外部系统配置表 - 对接的OA/ERP/MES等系统
-- ============================================================
DROP TABLE IF EXISTS task_external_system;
CREATE TABLE task_external_system (
    id              BIGINT          NOT NULL COMMENT '系统ID',
    system_code     VARCHAR(32)     NOT NULL COMMENT '系统编码: OA/ERP/MES/QMS...',
    system_name     VARCHAR(64)     NOT NULL COMMENT '系统名称',
    system_type     VARCHAR(32)     NOT NULL COMMENT '对接类型: REST/MQ/CDC/FILE',
    base_url        VARCHAR(256)    DEFAULT NULL COMMENT '接口基础地址',
    app_key         VARCHAR(128)    DEFAULT NULL COMMENT '应用Key',
    app_secret      VARCHAR(256)    DEFAULT NULL COMMENT '应用密钥(加密存储)',
    sync_enabled    TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用同步: 0-否, 1-是',
    sync_cron       VARCHAR(64)     DEFAULT '0 */5 * * * ?' COMMENT '同步频率Cron表达式',
    last_sync_time  DATETIME        DEFAULT NULL COMMENT '上次同步时间',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '删除标志',
    create_by       BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by       BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    remark          VARCHAR(512)    DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_code (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部系统配置表';

-- ============================================================
-- 4. 统一任务主表
-- ============================================================
DROP TABLE IF EXISTS task_info;
CREATE TABLE task_info (
    id                  BIGINT          NOT NULL COMMENT '任务ID',
    task_no             VARCHAR(64)     NOT NULL COMMENT '任务编号(系统生成)',
    external_task_id    VARCHAR(128)    DEFAULT NULL COMMENT '外部系统任务ID',
    external_system     VARCHAR(32)     NOT NULL COMMENT '来源系统编码: OA/ERP/MES/EB(自建)',
    task_type           VARCHAR(32)     NOT NULL COMMENT '任务类型: APPROVAL-审批, NOTICE-通知, TODO-待办, REVIEW-审核',
    task_category       VARCHAR(32)     DEFAULT NULL COMMENT '任务分类: 采购, 请假, 报销, 质量等',
    priority            TINYINT         NOT NULL DEFAULT 2 COMMENT '优先级: 1-紧急, 2-高, 3-中, 4-低',
    title               VARCHAR(256)    NOT NULL COMMENT '任务标题',
    content             TEXT            DEFAULT NULL COMMENT '任务内容/描述',
    biz_key             VARCHAR(128)    DEFAULT NULL COMMENT '业务单据号',
    biz_url             VARCHAR(512)    DEFAULT NULL COMMENT '业务跳转URL',
    initiator_id        BIGINT          DEFAULT NULL COMMENT '发起人ID',
    initiator_name      VARCHAR(64)     DEFAULT NULL COMMENT '发起人姓名',
    assignee_id         BIGINT          NOT NULL COMMENT '处理人ID',
    assignee_name       VARCHAR(64)     NOT NULL COMMENT '处理人姓名',
    assignee_dept_id    BIGINT          DEFAULT NULL COMMENT '处理人部门ID',
    assignee_dept_name  VARCHAR(64)     DEFAULT NULL COMMENT '处理人部门名称',
    cc_user_ids         VARCHAR(1024)   DEFAULT NULL COMMENT '抄送人ID列表(逗号分隔)',
    task_status         TINYINT         NOT NULL DEFAULT 0 COMMENT '任务状态: 0-待处理, 1-处理中, 2-已完成, 3-已驳回, 4-已撤销, 5-已超时, 6-已转办',
    receive_time        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
    deadline_time       DATETIME        DEFAULT NULL COMMENT '截止时间',
    start_process_time  DATETIME        DEFAULT NULL COMMENT '开始处理时间',
    complete_time       DATETIME        DEFAULT NULL COMMENT '完成时间',
    handle_duration     BIGINT          DEFAULT NULL COMMENT '处理耗时(秒)',
    is_read             TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    is_urged            TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已催办: 0-否, 1-是',
    urge_count          INT             NOT NULL DEFAULT 0 COMMENT '催办次数',
    action_result       VARCHAR(32)     DEFAULT NULL COMMENT '处理结果: AGREE-同意, REJECT-驳回, TRANSFER-转办, OTHER-其他',
    action_comment      VARCHAR(1024)   DEFAULT NULL COMMENT '处理意见',
    sort_weight         INT             NOT NULL DEFAULT 0 COMMENT '排序权重(值越大越靠前)',
    ext_data            JSON            DEFAULT NULL COMMENT '扩展数据(JSON格式)',
    deleted             TINYINT         NOT NULL DEFAULT 0 COMMENT '删除标志: 0-未删除, 1-已删除',
    create_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by           BIGINT          DEFAULT NULL COMMENT '更新人ID',
    update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    UNIQUE KEY uk_external (external_system, external_task_id),
    KEY idx_assignee_status (assignee_id, task_status),
    KEY idx_assignee_dept (assignee_dept_id, task_status),
    KEY idx_external_system (external_system),
    KEY idx_task_type (task_type),
    KEY idx_priority (priority),
    KEY idx_deadline (deadline_time),
    KEY idx_create_time (create_time),
    KEY idx_complete_time (complete_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一任务主表';

-- ============================================================
-- 5. 任务处理历史表（审批流轨迹）
-- ============================================================
DROP TABLE IF EXISTS task_handle_history;
CREATE TABLE task_handle_history (
    id                  BIGINT          NOT NULL COMMENT '记录ID',
    task_id             BIGINT          NOT NULL COMMENT '任务ID',
    node_code           VARCHAR(64)     DEFAULT NULL COMMENT '流程节点编码',
    node_name           VARCHAR(128)    DEFAULT NULL COMMENT '流程节点名称',
    handler_id          BIGINT          NOT NULL COMMENT '处理人ID',
    handler_name        VARCHAR(64)     NOT NULL COMMENT '处理人姓名',
    handler_dept_id     BIGINT          DEFAULT NULL COMMENT '处理人部门ID',
    handler_dept_name   VARCHAR(64)     DEFAULT NULL COMMENT '处理人部门名称',
    action_type         VARCHAR(32)     NOT NULL COMMENT '操作类型: CREATE-创建, CLAIM-领取, APPROVE-同意, REJECT-驳回, TRANSFER-转办, DELEGATE-委派, URGE-催办, COMMENT-评论, REVOKE-撤销',
    action_result       VARCHAR(32)     DEFAULT NULL COMMENT '处理结果',
    action_comment      VARCHAR(1024)   DEFAULT NULL COMMENT '处理意见/备注',
    previous_handler_id BIGINT          DEFAULT NULL COMMENT '上一处理人ID',
    next_handler_id     BIGINT          DEFAULT NULL COMMENT '下一处理人ID',
    attachments         VARCHAR(1024)   DEFAULT NULL COMMENT '附件URL(多个逗号分隔)',
    handle_duration     BIGINT          DEFAULT NULL COMMENT '节点处理耗时(秒)',
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_handler_id (handler_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务处理历史表';

-- ============================================================
-- 6. 任务转办/委派记录表
-- ============================================================
DROP TABLE IF EXISTS task_transfer;
CREATE TABLE task_transfer (
    id                  BIGINT          NOT NULL COMMENT '记录ID',
    task_id             BIGINT          NOT NULL COMMENT '任务ID',
    transfer_type       TINYINT         NOT NULL COMMENT '类型: 1-转办, 2-委派',
    from_user_id        BIGINT          NOT NULL COMMENT '原处理人ID',
    from_user_name      VARCHAR(64)     NOT NULL COMMENT '原处理人姓名',
    to_user_id          BIGINT          NOT NULL COMMENT '目标处理人ID',
    to_user_name        VARCHAR(64)     NOT NULL COMMENT '目标处理人姓名',
    reason              VARCHAR(512)    DEFAULT NULL COMMENT '转办原因',
    transfer_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '转办时间',
    is_return           TINYINT         NOT NULL DEFAULT 0 COMMENT '委派是否归还: 0-否, 1-是',
    return_time         DATETIME        DEFAULT NULL COMMENT '归还时间',
    PRIMARY KEY (id),
    KEY idx_task_id (task_id),
    KEY idx_from_user (from_user_id),
    KEY idx_to_user (to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务转办委派记录表';

-- ============================================================
-- 7. 操作日志表
-- ============================================================
DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
    id              BIGINT          NOT NULL COMMENT '日志ID',
    trace_id        VARCHAR(64)     DEFAULT NULL COMMENT '链路追踪ID',
    user_id         BIGINT          DEFAULT NULL COMMENT '操作人ID',
    username        VARCHAR(64)     DEFAULT NULL COMMENT '操作人账号',
    module          VARCHAR(64)     NOT NULL COMMENT '模块名称',
    operation       VARCHAR(128)    NOT NULL COMMENT '操作描述',
    method          VARCHAR(16)     DEFAULT NULL COMMENT '请求方法: GET/POST/PUT/DELETE',
    request_url     VARCHAR(512)    DEFAULT NULL COMMENT '请求URL',
    request_params  TEXT            DEFAULT NULL COMMENT '请求参数',
    response_result TEXT            DEFAULT NULL COMMENT '响应结果',
    ip_address      VARCHAR(64)     DEFAULT NULL COMMENT 'IP地址',
    user_agent      VARCHAR(512)    DEFAULT NULL COMMENT '用户代理',
    cost_time       BIGINT          DEFAULT NULL COMMENT '耗时(毫秒)',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 0-失败, 1-成功',
    error_msg       TEXT            DEFAULT NULL COMMENT '错误信息',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_module (module),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ============================================================
-- ============ 初始化数据 ============
-- ============================================================

-- 初始化部门数据
INSERT INTO sys_dept (id, parent_id, dept_name, dept_code, sort_order, leader, status) VALUES
(1, 0, '集团总部', 'HQ', 0, '张总', 1),
(2, 1, '技术研发部', 'RD', 1, '李总监', 1),
(3, 1, '质量管理部', 'QA', 2, '王总监', 1),
(4, 1, '生产制造部', 'PD', 3, '赵总监', 1),
(5, 1, '采购供应链部', 'SC', 4, '刘总监', 1),
(6, 1, '人力资源部', 'HR', 5, '陈总监', 1),
(7, 1, '财务管理部', 'FI', 6, '周总监', 1);

-- 初始化用户数据 (密码: 123456 - BCrypt加密)
INSERT INTO sys_user (id, username, password, real_name, email, phone, dept_id, status) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'admin@eb.com', '13800000000', 1, 1),
(2, 'zhangsan', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张三', 'zhangsan@eb.com', '13800000001', 3, 1),
(3, 'lisi', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李四', 'lisi@eb.com', '13800000002', 4, 1),
(4, 'wangwu', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '王五', 'wangwu@eb.com', '13800000003', 5, 1),
(5, 'zhaoliu', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '赵六', 'zhaoliu@eb.com', '13800000004', 2, 1);

-- 初始化外部系统配置
INSERT INTO task_external_system (id, system_code, system_name, system_type, base_url, sync_enabled, sync_cron, status, remark) VALUES
(1, 'OA', '协同办公系统', 'REST', 'http://oa.company.com/api', 1, '0 */5 * * * ?', 1, '泛微OA系统对接'),
(2, 'ERP', '企业资源计划系统', 'REST', 'http://erp.company.com/api', 1, '0 */3 * * * ?', 1, 'SAP ERP系统对接'),
(3, 'MES', '制造执行系统', 'MQ', NULL, 1, '0 */2 * * * ?', 1, 'MES生产任务同步'),
(4, 'EB', '企业大脑平台', 'REST', NULL, 1, NULL, 1, '平台自建任务');

-- 初始化示例任务数据
INSERT INTO task_info (id, task_no, external_system, external_task_id, task_type, task_category, priority, title, content,
    biz_key, initiator_id, initiator_name, assignee_id, assignee_name, assignee_dept_id, assignee_dept_name,
    task_status, receive_time, deadline_time, sort_weight) VALUES
(1001, 'TK202608150001', 'OA', 'OA-2026-00881', 'APPROVAL', 'LEAVE', 2, '【审批】张三-年假申请-5天',
    '申请日期：2026-08-20 至 2026-08-24，共5天年假，请审批。', 'LV-2026-0815-001',
    2, '张三', 1, '系统管理员', 1, '集团总部', 0, '2026-08-15 09:00:00', '2026-08-16 18:00:00', 100),
(1002, 'TK202608150002', 'ERP', 'ERP-PO-20260815001', 'APPROVAL', 'PURCHASE', 1, '【紧急审批】原材料采购订单审批-¥580,000',
    '采购供应商：XX材料科技有限公司，采购A类原材料10吨，单价58,000元/吨，合计580,000元。生产紧急需求，请尽快审批。',
    'PO-20260815-001', 4, '王五', 1, '系统管理员', 1, '集团总部', 0, '2026-08-15 10:30:00', '2026-08-15 18:00:00', 200),
(1003, 'TK202608150003', 'MES', 'MES-WO-2026-0815-012', 'TODO', 'PRODUCTION', 3, '【待办】生产工单执行-A产品-500件',
    '生产工单：WO-2026-0815-012，产品：A型号产品，数量：500件，计划完成日期：2026-08-20',
    'WO-2026-0815-012', 3, '李四', 3, '李四', 4, '生产制造部', 1, '2026-08-15 08:00:00', '2026-08-20 18:00:00', 50),
(1004, 'TK202608150004', 'EB', NULL, 'APPROVAL', 'QUALITY', 2, '【审批】不合格品处理申请-NCR20260815001',
    '质量异常报告：IQC来料检验发现批次B2026081401的电子元件不合格，申请退货处理，涉及金额¥35,000。',
    'NCR-20260815-001', 2, '张三', 1, '系统管理员', 1, '集团总部', 0, '2026-08-15 14:00:00', '2026-08-17 18:00:00', 80),
(1005, 'TK202608150005', 'OA', 'OA-2026-00876', 'APPROVAL', 'EXPENSE', 3, '【审批】李四-差旅费报销-¥3,280',
    '报销明细：上海出差往返机票¥1,800，酒店住宿¥1,200，餐饮¥280，合计¥3,280。',
    'EX-20260814-003', 3, '李四', 1, '系统管理员', 1, '集团总部', 2, '2026-08-14 16:00:00', NULL, 0),
(1006, 'TK202608150006', 'ERP', 'ERP-PO-20260810005', 'APPROVAL', 'PURCHASE', 2, '【审批】办公用品采购审批-¥12,500',
    '采购内容：办公电脑5台、打印耗材一批，合计¥12,500。',
    'PO-20260810-005', 4, '王五', 1, '系统管理员', 1, '集团总部', 2, '2026-08-10 11:00:00', NULL, 0);

-- 更新已完成任务的完成时间
UPDATE task_info SET start_process_time = '2026-08-14 17:00:00', complete_time = '2026-08-14 17:30:00',
    handle_duration = 1800, action_result = 'AGREE', action_comment = '同意报销，票据齐全。' WHERE id = 1005;
UPDATE task_info SET start_process_time = '2026-08-10 14:00:00', complete_time = '2026-08-10 15:20:00',
    handle_duration = 4800, action_result = 'AGREE', action_comment = '同意采购。' WHERE id = 1006;

-- 初始化任务处理历史
INSERT INTO task_handle_history (id, task_id, node_code, node_name, handler_id, handler_name, handler_dept_id, handler_dept_name,
    action_type, action_result, action_comment, create_time) VALUES
(1, 1001, 'START', '发起申请', 2, '张三', 3, '质量管理部', 'CREATE', NULL, '提交年假申请', '2026-08-15 09:00:00'),
(2, 1002, 'START', '创建订单', 4, '王五', 5, '采购供应链部', 'CREATE', NULL, '创建紧急采购订单', '2026-08-15 10:30:00'),
(3, 1003, 'START', '下发工单', 3, '李四', 4, '生产制造部', 'CREATE', NULL, '生产工单下发', '2026-08-15 08:00:00'),
(4, 1003, 'EXEC', '执行中', 3, '李四', 4, '生产制造部', 'CLAIM', NULL, '领取工单开始生产', '2026-08-15 08:30:00'),
(5, 1004, 'START', '提交NCR', 2, '张三', 3, '质量管理部', 'CREATE', NULL, '提交不合格品处理单', '2026-08-15 14:00:00'),
(6, 1005, 'START', '提交报销', 3, '李四', 4, '生产制造部', 'CREATE', NULL, '提交差旅费报销', '2026-08-14 16:00:00'),
(7, 1005, 'MANAGER', '主管审批', 1, '系统管理员', 1, '集团总部', 'APPROVE', 'AGREE', '同意报销，票据齐全。', '2026-08-14 17:30:00'),
(8, 1006, 'START', '提交采购', 4, '王五', 5, '采购供应链部', 'CREATE', NULL, '提交办公用品采购申请', '2026-08-10 11:00:00'),
(9, 1006, 'MANAGER', '主管审批', 1, '系统管理员', 1, '集团总部', 'APPROVE', 'AGREE', '同意采购。', '2026-08-10 15:20:00');
