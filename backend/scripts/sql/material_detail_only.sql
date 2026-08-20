-- 备料明细表 · 建表 + 数据（UTF-8 编码，PowerShell 按 GO 分块执行）
USE HLEIMS2026082008001;
GO
IF OBJECT_ID(N'dbo.mes_dwd_material_detail', N'U') IS NOT NULL DROP TABLE dbo.mes_dwd_material_detail;
GO
CREATE TABLE dbo.mes_dwd_material_detail (
    id                INT              IDENTITY(1,1) NOT NULL,
    task_id           VARCHAR(64)      NOT NULL,
    material_code     VARCHAR(64)      NOT NULL,
    material_name     NVARCHAR(200)    NOT NULL,
    specification     NVARCHAR(200)    NULL,
    unit              VARCHAR(20)      NOT NULL,
    required_qty      INT              NOT NULL,
    available_qty     INT              NOT NULL,
    prepared_qty      INT              NOT NULL,
    storage_location  VARCHAR(64)      NULL,
    status            VARCHAR(20)      NOT NULL,
    preparer          NVARCHAR(50)     NULL,
    prepare_time      DATETIME2        NULL,
    remark            NVARCHAR(500)    NULL,
    CONSTRAINT PK_mes_dwd_material_detail PRIMARY KEY CLUSTERED (id ASC)
);
GO
CREATE INDEX IX_md_task_id ON dbo.mes_dwd_material_detail(task_id);
GO
INSERT INTO dbo.mes_dwd_material_detail (task_id,material_code,material_name,specification,unit,required_qty,available_qty,prepared_qty,storage_location,status,preparer,prepare_time,remark) VALUES
('WO-20260815-001','M-PCB-001',N'主控PCB板',N'FR4 4层 100x80mm','块',500,500,500,'A-01-03','ready',N'张备料','2026-08-15 14:30:00',NULL),
('WO-20260815-001','M-IC-002',N'主控芯片STM32',N'STM32F407VGT6','颗',500,480,480,'B-02-01','shortage',N'张备料','2026-08-15 14:35:00',N'缺料20颗，已叫料'),
('WO-20260815-001','M-RES-003',N'贴片电阻',N'0402 10K ±1%','个',5000,5000,3000,'C-01-05','preparing',N'李备料','2026-08-15 15:00:00',NULL),
('WO-20260815-001','M-CAP-004',N'贴片电容',N'0402 100nF X7R','个',3000,3000,0,'C-02-03','pending',NULL,NULL,NULL),
('WO-20260815-001','M-CON-005',N'接线端子',N'KF128-2.54-2P','个',1000,1000,1000,'D-01-02','ready',N'张备料','2026-08-15 14:40:00',NULL),
('WO-20260815-001','M-CASE-006',N'铝合金外壳',N'150x100x40mm','套',500,500,500,'E-03-01','ready',N'李备料','2026-08-15 15:10:00',NULL),
('WO-20260815-002','M-PCB-101',N'网关主板',N'FR4 6层 120x100mm','块',300,300,0,'A-01-05','pending',NULL,NULL,NULL),
('WO-20260815-002','M-IC-102',N'通信模块',N'4G Cat.4 模组','个',300,250,0,'B-03-02','shortage',NULL,NULL,N'库存不足50个'),
('WO-20260815-002','M-ANT-103',N'天线',N'5dBi 全向','根',300,300,0,'D-02-04','pending',NULL,NULL,NULL),
('WO-20260815-002','M-PSU-104',N'电源适配器',N'12V/2A','个',300,300,0,'E-01-01','pending',NULL,NULL,NULL),
('WO-20260815-003','M-TRA-201',N'变压器',N'EE25 高频','个',1000,1000,1000,'F-01-01','ready',N'王备料','2026-08-15 10:00:00',NULL),
('WO-20260815-003','M-MOS-202',N'MOS管',N'IRF540N','个',4000,4000,4000,'B-04-03','ready',N'王备料','2026-08-15 10:15:00',NULL),
('WO-20260815-003','M-HEA-203',N'散热片',N'40x40x11mm 铝','片',1000,1000,1000,'E-02-05','ready',N'王备料','2026-08-15 10:30:00',NULL),
('WO-20260815-004','M-PCB-301',N'通信板PCB',N'FR4 4层 80x60mm','块',200,200,200,'A-02-01','ready',N'张备料','2026-08-15 09:00:00',NULL),
('WO-20260815-004','M-CON-302',N'RJ45网口连接器',N'HR911105A','个',400,400,400,'D-03-02','ready',N'张备料','2026-08-15 09:10:00',NULL),
('WO-20260815-004','M-IC-303',N'PHY芯片',N'DP83848CVV','颗',200,150,150,'B-05-01','shortage',N'张备料','2026-08-15 09:20:00',N'缺料50颗，已叫料'),
('WO-20260815-005','M-SEN-501',N'温度传感器',N'NTC 10K ±1%','个',800,800,0,'C-04-01','pending',NULL,NULL,NULL),
('WO-20260815-005','M-SEN-502',N'湿度传感器',N'HIH-4030','个',800,600,0,'C-04-02','pending',NULL,NULL,N'库存偏低'),
('WO-20260815-005','M-MOD-503',N'传感器外壳',N'IP65 40x30mm','套',800,800,0,'E-04-03','pending',NULL,NULL,NULL),
('WO-20260815-006','M-LCD-601',N'LCD显示屏',N'7寸 1024x600','块',150,150,100,'A-03-02','preparing',N'李备料','2026-08-15 14:00:00',NULL),
('WO-20260815-006','M-TPC-602',N'触摸板',N'7寸 电容式','块',150,150,0,'A-03-03','pending',NULL,NULL,NULL),
('WO-20260815-006','M-CASE-603',N'终端外壳',N'ABS 200x150x40mm','套',150,150,150,'E-05-01','ready',N'李备料','2026-08-15 14:20:00',NULL),
('WO-20260815-007','M-IGBT-701',N'IGBT模块',N'FF50R12RT4','个',400,400,0,'B-06-01','pending',NULL,NULL,NULL),
('WO-20260815-007','M-DRI-702',N'驱动板',N'FR4 2层 60x40mm','块',400,400,0,'A-04-02','pending',NULL,NULL,NULL),
('WO-20260815-008','M-MCU-801',N'MCU芯片',N'GD32F303RCT6','颗',600,600,600,'B-07-01','ready',N'王备料','2026-08-15 11:00:00',NULL),
('WO-20260815-008','M-PCB-802',N'采集板PCB',N'FR4 4层 60x50mm','块',600,600,600,'A-05-01','ready',N'王备料','2026-08-15 11:15:00',NULL),
('WO-20260815-008','M-CON-803',N'接线端子排',N'KF128-2.54-3P','排',1200,1200,1200,'D-04-01','ready',N'王备料','2026-08-15 11:30:00',NULL);
GO
