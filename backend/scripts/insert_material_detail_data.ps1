# 向已建好的 mes_dwd_material_detail 表插入 27 条 mock 数据（参数化查询，避免引号/编码问题）
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName 'System.Data'

$cs = 'Server=192.168.1.228;Database=HLEIMS2026082008001;User Id=sa;Password=Hltest@123;Encrypt=False;TrustServerCertificate=True;'
$conn = New-Object System.Data.SqlClient.SqlConnection($cs)
$conn.Open()
Write-Host "[*] Connected to $($conn.Database)"

# 先清空（表已建好）
$cmd = $conn.CreateCommand()
$cmd.CommandText = "TRUNCATE TABLE dbo.mes_dwd_material_detail;"
$cmd.ExecuteNonQuery() | Out-Null
Write-Host "    [OK] Truncated"

# 准备参数化 INSERT
$insertSql = "INSERT INTO dbo.mes_dwd_material_detail (task_id,material_code,material_name,specification,unit,required_qty,available_qty,prepared_qty,storage_location,status,preparer,prepare_time,remark) VALUES (@task_id,@material_code,@material_name,@specification,@unit,@required_qty,@available_qty,@prepared_qty,@storage_location,@status,@preparer,@prepare_time,@remark);"

# 27 条数据（PowerShell 数组，每条是一个 hashtable）
$rows = @(
    @{task_id='WO-20260815-001';material_code='M-PCB-001';material_name='主控PCB板';specification='FR4 4层 100x80mm';unit='块';required_qty=500;available_qty=500;prepared_qty=500;storage_location='A-01-03';status='ready';preparer='张备料';prepare_time='2026-08-15 14:30:00';remark=$null},
    @{task_id='WO-20260815-001';material_code='M-IC-002';material_name='主控芯片STM32';specification='STM32F407VGT6';unit='颗';required_qty=500;available_qty=480;prepared_qty=480;storage_location='B-02-01';status='shortage';preparer='张备料';prepare_time='2026-08-15 14:35:00';remark='缺料20颗，已叫料'},
    @{task_id='WO-20260815-001';material_code='M-RES-003';material_name='贴片电阻';specification='0402 10K ±1%';unit='个';required_qty=5000;available_qty=5000;prepared_qty=3000;storage_location='C-01-05';status='preparing';preparer='李备料';prepare_time='2026-08-15 15:00:00';remark=$null},
    @{task_id='WO-20260815-001';material_code='M-CAP-004';material_name='贴片电容';specification='0402 100nF X7R';unit='个';required_qty=3000;available_qty=3000;prepared_qty=0;storage_location='C-02-03';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-001';material_code='M-CON-005';material_name='接线端子';specification='KF128-2.54-2P';unit='个';required_qty=1000;available_qty=1000;prepared_qty=1000;storage_location='D-01-02';status='ready';preparer='张备料';prepare_time='2026-08-15 14:40:00';remark=$null},
    @{task_id='WO-20260815-001';material_code='M-CASE-006';material_name='铝合金外壳';specification='150x100x40mm';unit='套';required_qty=500;available_qty=500;prepared_qty=500;storage_location='E-03-01';status='ready';preparer='李备料';prepare_time='2026-08-15 15:10:00';remark=$null},
    @{task_id='WO-20260815-002';material_code='M-PCB-101';material_name='网关主板';specification='FR4 6层 120x100mm';unit='块';required_qty=300;available_qty=300;prepared_qty=0;storage_location='A-01-05';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-002';material_code='M-IC-102';material_name='通信模块';specification='4G Cat.4 模组';unit='个';required_qty=300;available_qty=250;prepared_qty=0;storage_location='B-03-02';status='shortage';preparer=$null;prepare_time=$null;remark='库存不足50个'},
    @{task_id='WO-20260815-002';material_code='M-ANT-103';material_name='天线';specification='5dBi 全向';unit='根';required_qty=300;available_qty=300;prepared_qty=0;storage_location='D-02-04';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-002';material_code='M-PSU-104';material_name='电源适配器';specification='12V/2A';unit='个';required_qty=300;available_qty=300;prepared_qty=0;storage_location='E-01-01';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-003';material_code='M-TRA-201';material_name='变压器';specification='EE25 高频';unit='个';required_qty=1000;available_qty=1000;prepared_qty=1000;storage_location='F-01-01';status='ready';preparer='王备料';prepare_time='2026-08-15 10:00:00';remark=$null},
    @{task_id='WO-20260815-003';material_code='M-MOS-202';material_name='MOS管';specification='IRF540N';unit='个';required_qty=4000;available_qty=4000;prepared_qty=4000;storage_location='B-04-03';status='ready';preparer='王备料';prepare_time='2026-08-15 10:15:00';remark=$null},
    @{task_id='WO-20260815-003';material_code='M-HEA-203';material_name='散热片';specification='40x40x11mm 铝';unit='片';required_qty=1000;available_qty=1000;prepared_qty=1000;storage_location='E-02-05';status='ready';preparer='王备料';prepare_time='2026-08-15 10:30:00';remark=$null},
    @{task_id='WO-20260815-004';material_code='M-PCB-301';material_name='通信板PCB';specification='FR4 4层 80x60mm';unit='块';required_qty=200;available_qty=200;prepared_qty=200;storage_location='A-02-01';status='ready';preparer='张备料';prepare_time='2026-08-15 09:00:00';remark=$null},
    @{task_id='WO-20260815-004';material_code='M-CON-302';material_name='RJ45网口连接器';specification='HR911105A';unit='个';required_qty=400;available_qty=400;prepared_qty=400;storage_location='D-03-02';status='ready';preparer='张备料';prepare_time='2026-08-15 09:10:00';remark=$null},
    @{task_id='WO-20260815-004';material_code='M-IC-303';material_name='PHY芯片';specification='DP83848CVV';unit='颗';required_qty=200;available_qty=150;prepared_qty=150;storage_location='B-05-01';status='shortage';preparer='张备料';prepare_time='2026-08-15 09:20:00';remark='缺料50颗，已叫料'},
    @{task_id='WO-20260815-005';material_code='M-SEN-501';material_name='温度传感器';specification='NTC 10K ±1%';unit='个';required_qty=800;available_qty=800;prepared_qty=0;storage_location='C-04-01';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-005';material_code='M-SEN-502';material_name='湿度传感器';specification='HIH-4030';unit='个';required_qty=800;available_qty=600;prepared_qty=0;storage_location='C-04-02';status='pending';preparer=$null;prepare_time=$null;remark='库存偏低'},
    @{task_id='WO-20260815-005';material_code='M-MOD-503';material_name='传感器外壳';specification='IP65 40x30mm';unit='套';required_qty=800;available_qty=800;prepared_qty=0;storage_location='E-04-03';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-006';material_code='M-LCD-601';material_name='LCD显示屏';specification='7寸 1024x600';unit='块';required_qty=150;available_qty=150;prepared_qty=100;storage_location='A-03-02';status='preparing';preparer='李备料';prepare_time='2026-08-15 14:00:00';remark=$null},
    @{task_id='WO-20260815-006';material_code='M-TPC-602';material_name='触摸板';specification='7寸 电容式';unit='块';required_qty=150;available_qty=150;prepared_qty=0;storage_location='A-03-03';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-006';material_code='M-CASE-603';material_name='终端外壳';specification='ABS 200x150x40mm';unit='套';required_qty=150;available_qty=150;prepared_qty=150;storage_location='E-05-01';status='ready';preparer='李备料';prepare_time='2026-08-15 14:20:00';remark=$null},
    @{task_id='WO-20260815-007';material_code='M-IGBT-701';material_name='IGBT模块';specification='FF50R12RT4';unit='个';required_qty=400;available_qty=400;prepared_qty=0;storage_location='B-06-01';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-007';material_code='M-DRI-702';material_name='驱动板';specification='FR4 2层 60x40mm';unit='块';required_qty=400;available_qty=400;prepared_qty=0;storage_location='A-04-02';status='pending';preparer=$null;prepare_time=$null;remark=$null},
    @{task_id='WO-20260815-008';material_code='M-MCU-801';material_name='MCU芯片';specification='GD32F303RCT6';unit='颗';required_qty=600;available_qty=600;prepared_qty=600;storage_location='B-07-01';status='ready';preparer='王备料';prepare_time='2026-08-15 11:00:00';remark=$null},
    @{task_id='WO-20260815-008';material_code='M-PCB-802';material_name='采集板PCB';specification='FR4 4层 60x50mm';unit='块';required_qty=600;available_qty=600;prepared_qty=600;storage_location='A-05-01';status='ready';preparer='王备料';prepare_time='2026-08-15 11:15:00';remark=$null},
    @{task_id='WO-20260815-008';material_code='M-CON-803';material_name='接线端子排';specification='KF128-2.54-3P';unit='排';required_qty=1200;available_qty=1200;prepared_qty=1200;storage_location='D-04-01';status='ready';preparer='王备料';prepare_time='2026-08-15 11:30:00';remark=$null}
)

$cnt = 0
foreach ($row in $rows) {
    $cmd = $conn.CreateCommand()
    $cmd.CommandText = $insertSql
    $cmd.Parameters.AddWithValue('@task_id', $row.task_id) | Out-Null
    $cmd.Parameters.AddWithValue('@material_code', $row.material_code) | Out-Null
    $cmd.Parameters.AddWithValue('@material_name', $row.material_name) | Out-Null
    if ($null -eq $row.specification) { $cmd.Parameters.AddWithValue('@specification', [DBNull]::Value) | Out-Null } else { $cmd.Parameters.AddWithValue('@specification', $row.specification) | Out-Null }
    $cmd.Parameters.AddWithValue('@unit', $row.unit) | Out-Null
    $cmd.Parameters.AddWithValue('@required_qty', $row.required_qty) | Out-Null
    $cmd.Parameters.AddWithValue('@available_qty', $row.available_qty) | Out-Null
    $cmd.Parameters.AddWithValue('@prepared_qty', $row.prepared_qty) | Out-Null
    if ($null -eq $row.storage_location) { $cmd.Parameters.AddWithValue('@storage_location', [DBNull]::Value) | Out-Null } else { $cmd.Parameters.AddWithValue('@storage_location', $row.storage_location) | Out-Null }
    $cmd.Parameters.AddWithValue('@status', $row.status) | Out-Null
    if ($null -eq $row.preparer) { $cmd.Parameters.AddWithValue('@preparer', [DBNull]::Value) | Out-Null } else { $cmd.Parameters.AddWithValue('@preparer', $row.preparer) | Out-Null }
    if ($null -eq $row.prepare_time) { $cmd.Parameters.AddWithValue('@prepare_time', [DBNull]::Value) | Out-Null } else { $cmd.Parameters.AddWithValue('@prepare_time', [DateTime]::Parse($row.prepare_time)) | Out-Null }
    if ($null -eq $row.remark) { $cmd.Parameters.AddWithValue('@remark', [DBNull]::Value) | Out-Null } else { $cmd.Parameters.AddWithValue('@remark', $row.remark) | Out-Null }
    $cmd.ExecuteNonQuery() | Out-Null
    $cnt++
}
Write-Host "    [OK] Inserted $cnt rows"

# 验证
$cmd = $conn.CreateCommand()
$cmd.CommandText = "SELECT COUNT(*) AS cnt, COUNT(DISTINCT task_id) AS tasks FROM dbo.mes_dwd_material_detail;"
$r = $cmd.ExecuteReader()
if ($r.Read()) { Write-Host "[VERIFY] rows=$($r['cnt']) distinct_tasks=$($r['tasks'])" }
$r.Close()

$cmd2 = $conn.CreateCommand()
$cmd2.CommandText = "SELECT status, COUNT(*) AS c FROM dbo.mes_dwd_material_detail GROUP BY status ORDER BY status;"
$r2 = $cmd2.ExecuteReader()
Write-Host "[STATUS DIST]"
while ($r2.Read()) { Write-Host "    $($r2['status']) = $($r2['c'])" }
$r2.Close()

$conn.Close()
Write-Host "[*] Done."
