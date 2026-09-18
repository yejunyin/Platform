---
name: eb-task-jar-package
description: Build, package, stop or restart the eb-service-task backend jar on this Windows workspace (JDK17, port 8081). Use for 打包/重新打包/部署/重启后端 requests. Not for frontend builds.
---

# eb-service-task 打包与启停

Windows PowerShell 环境。所有命令在 PowerShell 中执行，链式命令用 `;`，禁止用 `&&`。

## 固定路径

- 模块目录：`e:\叶的共享文件\Platform\backend\eb-service-task`
- 产物：`e:\叶的共享文件\Platform\backend\eb-service-task\target\eb-service-task-1.0.0.jar`（约 107-113 MB fat jar）
- JDK 17（必须，默认 JAVA_HOME 是 JDK8 会报 "无效的标记: --release"）：`D:\Java\jdk-17.0.20.1`
- Maven：系统 PATH 中的 `mvn`（E:\apache-maven-3.9.15），无需另装
- pid 文件：`e:\叶的共享文件\Platform\backend\backend.pid`
- 日志：`e:\叶的共享文件\Platform\backend\.tools\backend.log` 与 `backend.err.log`
- 服务端口：8081；数据源为远程 SQL Server 192.168.1.228:1433，不依赖本地 MySQL

## 打包步骤

1. 若服务在运行，先停掉（Windows 下运行中的 jar 锁定 target 文件，clean 会失败）：
   - 读 `backend.pid` 取 PID，确认进程名是 java 后 `Stop-Process -Id <pid> -Force`
   - 或按端口查：`Get-NetTCPConnection -LocalPort 8081 -State Listen` 取 OwningProcess
   - 停后 `Start-Sleep 3`，再次确认 8081 无 Listen 监听
2. 在模块目录执行（每条命令前都要设置 JAVA_HOME，Shell 会话间环境不保留）：
   ```powershell
   $env:JAVA_HOME='D:\Java\jdk-17.0.20.1'; mvn clean package '-Dmaven.test.skip=true' -q
   ```
   - 必须跳过测试：`src/test/.../TaskServiceTest.java` 有既存编译问题（JDK17 下 var 用法报错），`-DskipTests` 仍会编译测试源码、不够，必须用 `-Dmaven.test.skip=true`
   - 确认 `$LASTEXITCODE` 为 0
3. 校验产物：`Get-ChildItem target\*.jar` 确认 LastWriteTime 是本次时间、大小约 107 MB。
4. 若改动涉及金蝶报文字段串（如 FMTONO/FMtoNo），从 jar 中解压对应 class 用 `Select-String` 确认字段已编入，再汇报完成，避免误报。

## 启动（用户要求部署/启动时才做）

本地无 Nacos(8848)/Redis(6379) 时，用单机参数后台启动（这些参数经过验证）：

```powershell
$jar='e:\叶的共享文件\Platform\backend\eb-service-task\target\eb-service-task-1.0.0.jar'
$tools='e:\叶的共享文件\Platform\backend\.tools'
$argList=@('-jar',$jar,
 '--spring.main.lazy-initialization=true',
 '--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV2Reactive',
 '--management.health.redis.enabled=false',
 '--spring.cloud.nacos.discovery.enabled=false',
 '--spring.cloud.nacos.config.enabled=false',
 '--spring.cloud.service-registry.auto-registration.enabled=false',
 '--spring.cloud.nacos.discovery.register-enabled=false',
 '--spring.data.redis.host=localhost','--spring.data.redis.port=6379','--spring.data.redis.timeout=500')
$proc=Start-Process -FilePath 'D:\Java\jdk-17.0.20.1\bin\java.exe' -ArgumentList $argList -PassThru `
  -RedirectStandardOutput "$tools\backend.log" -RedirectStandardError "$tools\backend.err.log" -WindowStyle Hidden
$proc.Id | Out-File 'e:\叶的共享文件\Platform\backend\backend.pid' -Encoding ASCII
```

验证：轮询 8081 监听（通常 10 秒内），确认监听者 PID 与启动 PID 一致，再等约 12 秒后
`Invoke-RestMethod http://localhost:8081/api/v1/material-call/tasks` 返回 code=200；
日志中出现 `Started TaskServiceApplication` 且 err 日志为 0 字节才算成功。

## 注意

- 只处理 eb-service-task，不要顺带打包前端或其他模块，除非用户明确要求。
- 停止进程后必须复查端口状态，禁止在未核实的情况下声称已停止/已启动。
- 金蝶键名大小写敏感：补料单 PRD_FeedMtrl 计划跟踪号是 FMTONO（全大写），退料单 PRD_ReturnMtrl 是 FMtoNo，二者来源均为用料清单 PRD_PPBOM 的 FMTONO。
