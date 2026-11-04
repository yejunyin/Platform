package com.enterprise.brain.task.wms.oneclickpick.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.enterprise.brain.task.wms.dto.WmsPickingSendResponse;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPick;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPickEntry;
import com.enterprise.brain.task.wms.oneclickpick.entity.WmsOneClickPickLog;
import com.enterprise.brain.task.wms.oneclickpick.mapper.WmsOneClickPickEntryMapper;
import com.enterprise.brain.task.wms.oneclickpick.mapper.WmsOneClickPickLogMapper;
import com.enterprise.brain.task.wms.oneclickpick.mapper.WmsOneClickPickMapper;
import com.enterprise.brain.task.service.WmsService;
import com.enterprise.brain.task.wms.service.WmsPickingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一键领料任务调度器（料架驱动模式）
 *
 * 核心逻辑：
 *  1. 启动时恢复 running 任务
 *  2. 每轮先查 WMS 料架库存 → Map<productionNo, Map<materialCode, onShelfQty>>
 *  3. 过滤本轮可推物料：sentQty < totalQty 且料架上该物料数量 == 0
 *  4. 组装 WMS ProductionPicking JSON 并推送
 *  5. 如果剩下 ≤8 种物料，放宽料架限制（料架上有也可以继续推，防止最后一批卡死）
 *  6. WMS 限制：每次最多 48 箱 / 6 货架（组装时按物料种类数控制）
 *  7. 推完后按 intervalSec 安排下一轮轮询
 */
@Slf4j
@Component
public class OneClickPickScheduler {

    @Resource
    private WmsOneClickPickMapper taskMapper;

    @Resource
    private WmsOneClickPickEntryMapper entryMapper;

    @Resource
    private WmsOneClickPickLogMapper logMapper;

    @Resource
    private WmsService wmsService;

    @Resource
    private WmsPickingService pickingService;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Value("${one-click-pick.enabled:false}")
    private boolean enabled;

    /** WMS 料架库存查询地址 */
    @Value("${one-click-pick.shelf-url:http://192.168.15.20:8081/prod-api/api/upperSystem/shelf/inventory}")
    private String shelfInventoryUrl;

    /** WMS 料架库存查询超时（毫秒） */
    @Value("${one-click-pick.shelf-timeout-ms:3000}")
    private int shelfTimeoutMs;

    /** WMS 单次推送最多物料种类（对应 48 箱 / 6 货架 限制） */
    @Value("${one-click-pick.max-round-materials:48}")
    private int maxRoundMaterials;

    /** 剩余物料种类 ≤ maxRoundMaterials 时，触发 relax 模式（剩余框位全分配） */

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 调度线程池 */
    private ScheduledThreadPoolExecutor scheduler;

    /** taskId → Future 映射 */
    private final ConcurrentMap<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /** taskId → 运行中标记 */
    private final ConcurrentMap<Long, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /** 专用 RestTemplate（只读 shelf inventory） */
    private volatile RestTemplate shelfRestTemplate;

    // ============ 初始化 ============

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("[一键领料] one-click-pick.enabled=false，调度器已禁用");
            return;
        }
        scheduler = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r, "OneClickPickScheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.setRemoveOnCancelPolicy(true);

        restoreRunningTasks();
    }

    private RestTemplate getShelfRestTemplate() {
        if (shelfRestTemplate == null) {
            synchronized (this) {
                if (shelfRestTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(shelfTimeoutMs);
                    factory.setReadTimeout(shelfTimeoutMs);
                    shelfRestTemplate = new RestTemplate(factory);
                }
            }
        }
        return shelfRestTemplate;
    }

    private void restoreRunningTasks() {
        List<WmsOneClickPick> running = taskMapper.selectList(
                new QueryWrapper<WmsOneClickPick>().eq("status", "running"));
        log.info("[一键领料] 启动恢复 running 任务数: {}", running.size());
        for (WmsOneClickPick task : running) {
            scheduleNext(task, task.getNextFireTime());
        }
    }

    // ============ 料架库存查询 ============

    /**
     * 查 WMS 料架库存接口
     * @return Map<productionNo, Map<materialCode, quantityOnShelf>>
     */
    private Map<String, Map<String, BigDecimal>> queryShelfInventory() {
        Map<String, Map<String, BigDecimal>> result = new HashMap<>();
        try {
            ResponseEntity<String> resp = getShelfRestTemplate().getForEntity(shelfInventoryUrl, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[一键领料] 料架接口 HTTP {}，跳过料架驱动，本轮全部放行", resp.getStatusCode());
                return result;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (root.path("Success").asBoolean() != true) {
                log.warn("[一键领料] 料架接口 Success=false，Message={}，跳过料架驱动", root.path("Message").asText());
                return result;
            }
            JsonNode shelves = root.path("Data").path("Shelves");
            if (!shelves.isArray()) return result;

            for (JsonNode shelf : shelves) {
                JsonNode items = shelf.path("Items");
                if (!items.isArray()) continue;
                for (JsonNode item : items) {
                    String prodNo = item.path("ProdctionNo").asText();
                    String mc = item.path("MaterialCode").asText();
                    BigDecimal qty = new BigDecimal(item.path("Quantity").asInt(0));
                    if (prodNo.isBlank() || mc.isBlank()) continue;

                    result.computeIfAbsent(prodNo, k -> new HashMap<>())
                          .merge(mc, qty, BigDecimal::add);
                }
            }
            log.info("[一键领料] 料架查询成功，涉及生产订单 {} 个", result.size());
        } catch (Exception ex) {
            log.warn("[一键领料] 料架查询失败（{}），跳过料架驱动，本轮全部放行: {}", shelfInventoryUrl, ex.getMessage());
        }
        return result;
    }

    // ============ 调度控制 ============

    public void scheduleNext(WmsOneClickPick task, long delaySec) {
        if (!enabled) {
            log.warn("[一键领料] enabled=false，忽略 schedule taskId={}", task.getId());
            return;
        }
        if (scheduler == null) {
            log.warn("[一键领料] scheduler 未初始化，忽略 schedule taskId={}", task.getId());
            return;
        }
        long taskId = task.getId();
        AtomicBoolean running = runningFlags.computeIfAbsent(taskId, k -> new AtomicBoolean(true));
        if (!running.get()) {
            log.info("[一键领料] taskId={} 已暂停/取消，跳过 schedule", taskId);
            return;
        }

        ScheduledFuture<?> oldFuture = futures.get(taskId);
        if (oldFuture != null && !oldFuture.isDone()) oldFuture.cancel(false);

        ScheduledFuture<?> future = scheduler.schedule(() -> executeOneRound(taskId),
                delaySec, TimeUnit.SECONDS);
        futures.put(taskId, future);
        log.info("[一键领料] taskId={} 已 schedule，delay={}s", taskId, delaySec);
    }

    public void scheduleNext(WmsOneClickPick task, LocalDateTime nextFireTime) {
        long delaySec = nextFireTime == null ? 0
                : Math.max(0, Duration.between(LocalDateTime.now(), nextFireTime).getSeconds());
        scheduleNext(task, delaySec);
    }

    // ============ 核心：执行一轮（料架驱动） ============

    private void executeOneRound(long taskId) {
        log.info("[一键领料] ===== 开始执行 taskId={} 一轮 =====", taskId);

        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) { cleanup(taskId, false); return; }
        AtomicBoolean running = runningFlags.get(taskId);
        if (running == null || !running.get()) {
            log.info("[一键领料] taskId={} 已暂停/取消，本轮跳过", taskId);
            return;
        }
        if (!"running".equals(task.getStatus())) { cleanup(taskId, false); return; }

        List<WmsOneClickPickEntry> entries = entryMapper.selectList(
                new QueryWrapper<WmsOneClickPickEntry>().eq("task_id", taskId));

        LocalDateTime now = LocalDateTime.now();

        // ========== Phase A: 查 WMS 料架 → 闭环确认"释放成功" ==========
        Map<String, Map<String, BigDecimal>> shelfMap = queryShelfInventory();
        Map<String, BigDecimal> shelfForTask = shelfMap.getOrDefault(task.getProductionNo(), Collections.emptyMap());
        boolean shelfAvailable = !shelfMap.isEmpty();

        int releasedDelta = 0;   // 本轮因释放而增加的 sentQty 次数（>0 表示"查到物料被输送"）
        int confirmedDelta = 0; // 本轮新确认上料次数

        if (shelfAvailable) {

            for (WmsOneClickPickEntry e : entries) {
                BigDecimal prev = e.getLastOnShelfQty();
                if (prev == null) prev = BigDecimal.ZERO;
                BigDecimal curr = shelfForTask.getOrDefault(e.getMaterialCode(), BigDecimal.ZERO);

                boolean prevZero = prev.compareTo(BigDecimal.ZERO) == 0;
                boolean currZero = curr.compareTo(BigDecimal.ZERO) == 0;

                if (!prevZero && currZero) {
                    // ── 情况1: 料架上之前有，现在没了 → WMS 释放成功 ──
                    BigDecimal newSent = safe(e.getSentQty()).add(e.getPerBoxQty());
                    int newReleased = safeInt(e.getReleasedTimes()) + 1;
                    int newConfirmed = safeInt(e.getConfirmedTimes()) + 1;
                    UpdateWrapper<WmsOneClickPickEntry> uw = new UpdateWrapper<>();
                    uw.eq("id", e.getId())
                      .set("sent_qty", newSent)
                      .set("released_times", newReleased)
                      .set("confirmed_times", newConfirmed)
                      .set("last_on_shelf_qty", BigDecimal.ZERO)
                      .set("last_confirmed_time", now)
                      .set("update_time", now);
                    entryMapper.update(null, uw);
                    e.setSentQty(newSent);
                    e.setReleasedTimes(newReleased);
                    e.setConfirmedTimes(newConfirmed);
                    e.setLastOnShelfQty(BigDecimal.ZERO);
                    releasedDelta++;
                    log.info("[一键领料] taskId={} 物料 {} 料架释放（prev={}→curr=0），sentQty→{} released_times→{}",
                            taskId, e.getMaterialCode(), prev, newSent, newReleased);

                } else if (prevZero && !currZero) {
                    // ── 情况2: 之前无，现在有 → WMS 刚上料 ──
                    int newConfirmed = safeInt(e.getConfirmedTimes()) + 1;
                    UpdateWrapper<WmsOneClickPickEntry> uw = new UpdateWrapper<>();
                    uw.eq("id", e.getId())
                      .set("confirmed_times", newConfirmed)
                      .set("last_on_shelf_qty", curr)
                      .set("last_confirmed_time", now)
                      .set("update_time", now);
                    entryMapper.update(null, uw);
                    e.setConfirmedTimes(newConfirmed);
                    e.setLastOnShelfQty(curr);
                    confirmedDelta++;
                    log.info("[一键领料] taskId={} 物料 {} 料架新上料 {}，confirmed_times→{}",
                            taskId, e.getMaterialCode(), curr, newConfirmed);

                } else if (!prevZero && !currZero && prev.compareTo(curr) != 0) {
                    // ── 情况3: 料架上数量变化（有增有减）→ 只更新 lastOnShelfQty ──
                    UpdateWrapper<WmsOneClickPickEntry> uw = new UpdateWrapper<>();
                    uw.eq("id", e.getId())
                      .set("last_on_shelf_qty", curr)
                      .set("last_confirmed_time", now)
                      .set("update_time", now);
                    entryMapper.update(null, uw);
                    e.setLastOnShelfQty(curr);
                }
                // prev==curr 全部零 或 全部相同 → 什么都不做
            }

            log.info("[一键领料] taskId={} 料架闭环: released+{} confirmed+{} shelfAvailable={}",
                    taskId, releasedDelta, confirmedDelta, shelfAvailable);
        } else {
            log.warn("[一键领料] taskId={} 料架接口不可用，跳过闭环确认", taskId);
        }

        // ========== Phase B: 检查是否全部发满 ==========
        boolean allDone = true;
        int remainingCount = 0;
        for (WmsOneClickPickEntry e : entries) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) {
                allDone = false;
                remainingCount++;
            }
        }
        if (allDone) {
            completeTask(task, "所有明细发料已满（料架闭环确认）");
            return;
        }

        // ========== Phase C: 选本轮要推的物料（动态框位分配） ==========
        boolean firstRound = task.getCurrentRound() == null || task.getCurrentRound() == 0;

        // 过滤: 未发满
        List<WmsOneClickPickEntry> candidates = new ArrayList<>();
        int totalInFlight = 0; // 所有未发满物料的在途总数
        for (WmsOneClickPickEntry e : entries) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) {
                candidates.add(e);
                totalInFlight += e.getInFlightTimes();
            }
        }
        boolean relax = !firstRound && candidates.size() <= maxRoundMaterials;

        // 排序优先级（三层）:
        //   1. 在途少的优先（优先清在途）
        //   2. sent_times 少的优先（轮询均衡）
        //   3. current_round 少的优先
        candidates.sort((a, b) -> {
            int c1 = Integer.compare(a.getInFlightTimes(), b.getInFlightTimes());
            if (c1 != 0) return c1;
            int c2 = Integer.compare(safeInt(a.getSentTimes()), safeInt(b.getSentTimes()));
            if (c2 != 0) return c2;
            return Integer.compare(safeInt(a.getCurrentRound()), safeInt(b.getCurrentRound()));
        });

        BigDecimal[] roundQtyArr = new BigDecimal[entries.size()];
        Arrays.fill(roundQtyArr, BigDecimal.ZERO);
        // 记录每种物料本轮推了几"箱"（=sent_times 增量 = inFlight 增量）
        Map<String, Integer> roundSentTimesInc = new LinkedHashMap<>();

        int pickedMaterials = 0;
        int pickedInFlightSum = totalInFlight;

        if (firstRound) {
            // ── 首轮: 全部物料推一遍，不限种类数和框数 ──
            for (WmsOneClickPickEntry e : candidates) {
                BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
                if (left.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal roundQty = left.min(e.getPerBoxQty());
                if (roundQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                int idx = entries.indexOf(e);
                roundQtyArr[idx] = roundQty;
                pickedMaterials++;
                pickedInFlightSum += 1;
                roundSentTimesInc.put(e.getMaterialCode(), 1);
            }
            log.info("[一键领料] taskId={} 首轮: 全部 {} 种推一遍（跳过料架）", taskId, pickedMaterials);

        } else if (relax) {
            // ── relax 模式: 剩余物料 ≤ maxRoundMaterials，把空框全分配出去 ──
            int freeBoxes = maxRoundMaterials - totalInFlight;
            if (freeBoxes < 0) freeBoxes = 0;

            // 计算每种物料还需要几框
            Map<String, Integer> needBoxes = new LinkedHashMap<>();
            int totalNeedBoxes = 0;
            for (WmsOneClickPickEntry e : candidates) {
                BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
                int nb = left.divide(safe(e.getPerBoxQty()), 0, RoundingMode.CEILING).intValue();
                if (nb < 1) nb = 1;
                needBoxes.put(e.getMaterialCode(), nb);
                totalNeedBoxes += nb;
            }

            log.info("[一键领料] taskId={} relax模式: 物料={} 总需框={} 空框={} 在途={}",
                    taskId, candidates.size(), totalNeedBoxes, freeBoxes, totalInFlight);

            // 贪心分配: 按排序顺序（在途少→sent_times少），能拿多少拿多少
            for (WmsOneClickPickEntry e : candidates) {
                if (freeBoxes <= 0) break;

                int canTake = Math.min(needBoxes.get(e.getMaterialCode()), freeBoxes);
                if (canTake <= 0) continue;

                BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
                BigDecimal roundQty = safe(e.getPerBoxQty()).multiply(BigDecimal.valueOf(canTake));
                if (roundQty.compareTo(left) > 0) roundQty = left; // 最后截断
                if (roundQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                int actualBoxes = roundQty.divide(safe(e.getPerBoxQty()), 0, RoundingMode.DOWN).intValue();
                if (actualBoxes <= 0) actualBoxes = 1;

                int idx = entries.indexOf(e);
                roundQtyArr[idx] = roundQty;

                pickedMaterials++;
                pickedInFlightSum += actualBoxes;
                roundSentTimesInc.put(e.getMaterialCode(), actualBoxes);
                freeBoxes -= actualBoxes;

                log.info("[一键领料] taskId={} relax分配: 物料{} 需{} 拿{} 剩余空框={}",
                        taskId, e.getMaterialCode(), needBoxes.get(e.getMaterialCode()), actualBoxes, freeBoxes);
            }

        } else {
            // ── 正常模式: 本轮推前 maxRoundMaterials 种，每种 1 箱 ──
            for (WmsOneClickPickEntry e : candidates) {
                if (pickedMaterials >= maxRoundMaterials) break;

                BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
                if (left.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal roundQty = left.min(e.getPerBoxQty());
                if (roundQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                int idx = entries.indexOf(e);
                roundQtyArr[idx] = roundQty;

                pickedMaterials++;
                pickedInFlightSum += 1;
                roundSentTimesInc.put(e.getMaterialCode(), 1);
            }
        }

        log.info("[一键领料] taskId={} firstRound={} relax={} picked={} totalInFlight={} pickedInFlightAfter={} maxRound={}",
                taskId, firstRound, relax, pickedMaterials, totalInFlight, pickedInFlightSum, maxRoundMaterials);

        if (pickedMaterials == 0) {
            log.info("[一键领料] taskId={} 本轮无可推物料，安排下一次轮询", taskId);
            scheduleNext(task, computeDelaySec(task, releasedDelta));
            return;
        }

        // ========== Phase D: 组装 WMS JSON → 推送 ==========
        String billNo = pickingService.generateNextBillNo(task.getProductionNo());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("BillCode", billNo);
        data.put("ProdctionNo", task.getProductionNo());
        data.put("ProductCode", task.getProductCode());
        data.put("ProductName", task.getProductName());
        data.put("OrderTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "+08:00");
        data.put("OrganizationCode", task.getOrgCode());
        data.put("DepartmentCode", task.getDeptCode());
        data.put("OrderType", 1);
        data.put("DocumentType", "ProductionPicking");
        data.put("CustomerCode", null);
        data.put("CostCenterCode", task.getOrgCode());
        data.put("LineCode", task.getLineCode());

        List<Map<String, Object>> wmsEntries = new ArrayList<>();
        int itemNo = 1;
        for (int i = 0; i < entries.size(); i++) {
            BigDecimal qty = roundQtyArr[i];
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            WmsOneClickPickEntry e = entries.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ItemNo", itemNo++);
            m.put("MaterialCode", e.getMaterialCode());
            m.put("BatchCode", null);
            m.put("Quantity", qty);
            m.put("UnitCode", e.getUnitCode() != null ? e.getUnitCode() : "");
            m.put("ProductionStationCode", e.getStationCode() != null ? e.getStationCode() : "3SC01");
            m.put("OutWarehouseCode", e.getWarehouseCode() != null ? e.getWarehouseCode() : "2010");
            wmsEntries.add(m);
        }
        data.put("Entries", wmsEntries);

        log.info("[一键领料] taskId={} round={} billNo={} 推 WMS，物料={} 种",
                taskId, task.getCurrentRound() + 1, billNo, pickedMaterials);
        try {
            log.info("[一键领料] ====== WMS JSON ======\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
        } catch (Exception ignored) {}

        String logStatus, logMessage;
        String wmsTaskNo = null, requestId = null;
        boolean wmsSuccess = false;
        try {
            WmsPickingSendResponse resp = wmsService.sendProductionPicking(data, task.getLineCode());
            logMessage = resp.getMessage();
            if (resp.getData() != null) {
                wmsTaskNo = resp.getData().getWmsTaskNo();
                requestId = resp.getData().getRequestId();
            }
            if (Boolean.TRUE.equals(resp.getSuccess())) {
                logStatus = "sent"; wmsSuccess = true;
            } else {
                logStatus = "failed";
            }
        } catch (RuntimeException ex) {
            logStatus = "failed";
            logMessage = ex.getMessage();
        }
        log.info("[一键领料] taskId={} WMS 结果: status={}, msg={}", taskId, logStatus, logMessage);

        // ========== Phase E: 更新 sent_times（推一次+1）+ 写日志 ==========
        final boolean finalWmsSuccess = wmsSuccess;
        final String finalLogStatus = logStatus;
        final String finalLogMessage = logMessage;
        final String finalWmsTaskNo = wmsTaskNo;
        final String finalRequestId = requestId;
        final BigDecimal[] finalRoundQtyArr = roundQtyArr;
        final Map<String, Integer> finalRoundSentTimesInc = roundSentTimesInc;

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            int roundNum = task.getCurrentRound() + 1;

            for (int i = 0; i < entries.size(); i++) {
                BigDecimal rq = finalRoundQtyArr[i];
                if (rq.compareTo(BigDecimal.ZERO) <= 0) continue;
                if (!finalWmsSuccess) continue;

                WmsOneClickPickEntry e = entries.get(i);
                int incTimes = finalRoundSentTimesInc.getOrDefault(e.getMaterialCode(), 1);
                int newSentTimes = safeInt(e.getSentTimes()) + incTimes;
                int newCurRound = safeInt(e.getCurrentRound()) + 1;
                UpdateWrapper<WmsOneClickPickEntry> uw = new UpdateWrapper<>();
                uw.eq("id", e.getId())
                  .set("sent_times", newSentTimes)
                  .set("current_round", newCurRound)
                  .set("update_time", now);
                entryMapper.update(null, uw);
            }

            // 日志
            WmsOneClickPickLog log = new WmsOneClickPickLog();
            log.setTaskId(task.getId());
            log.setRoundNo(roundNum);
            log.setBillNo(billNo);
            log.setStatus(finalLogStatus);
            log.setWmsTaskNo(finalWmsTaskNo);
            log.setRequestId(finalRequestId);
            log.setMessage(finalLogMessage != null && finalLogMessage.length() > 1000
                    ? finalLogMessage.substring(0, 1000) : finalLogMessage);
            log.setCreateTime(now);
            logMapper.insert(log);

            // 任务头
            UpdateWrapper<WmsOneClickPick> tu = new UpdateWrapper<>();
            tu.eq("id", task.getId())
              .set("current_round", roundNum)
              .set("last_fire_time", now)
              .set("success_count", safeInt(task.getSuccessCount()) + (finalWmsSuccess ? 1 : 0))
              .set("fail_count", safeInt(task.getFailCount()) + (finalWmsSuccess ? 0 : 1))
              .set("message", finalLogMessage != null && finalLogMessage.length() > 500
                    ? finalLogMessage.substring(0, 500) : finalLogMessage)
              .set("update_time", now);
            int intervalMin = task.getIntervalSec() == null ? 1 : task.getIntervalSec();
            tu.set("next_fire_time", now.plusMinutes(intervalMin));
            taskMapper.update(null, tu);
            return null;
        });

        if (!wmsSuccess) {
            log.warn("[一键领料] taskId={} WMS 推送失败，任务自动停止", taskId);
            failTask(task, finalLogMessage);
            return;
        }

        // ========== Phase F: 查最新状态 → 决定 complete / 下一轮 ==========
        List<WmsOneClickPickEntry> refreshed = entryMapper.selectList(
                new QueryWrapper<WmsOneClickPickEntry>().eq("task_id", taskId));
        boolean allDoneNow = true;
        for (WmsOneClickPickEntry e : refreshed) {
            BigDecimal left = e.getTotalQty().subtract(safe(e.getSentQty()));
            if (left.compareTo(BigDecimal.ZERO) > 0) { allDoneNow = false; break; }
        }
        if (allDoneNow) {
            completeTask(task, "所有明细发料已满");
            return;
        }

        WmsOneClickPick latest = taskMapper.selectById(taskId);
        if ("running".equals(latest.getStatus())) {
            scheduleNext(latest, computeDelaySec(latest, releasedDelta));
        }
    }

    // ============ 任务状态 ============

    private void cleanup(long taskId, boolean removeTaskFromMap) {
        if (removeTaskFromMap) {
            runningFlags.remove(taskId);
            futures.remove(taskId);
        }
    }

    private void failTask(WmsOneClickPick task, String reason) {
        log.info("[一键领料] taskId={} 失败停止: {}", task.getId(), reason);
        UpdateWrapper<WmsOneClickPick> uw = new UpdateWrapper<>();
        uw.eq("id", task.getId())
          .set("status", "failed")
          .set("message", reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason)
          .set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        ScheduledFuture<?> f = futures.remove(task.getId());
        if (f != null) f.cancel(false);
        runningFlags.remove(task.getId());
    }

    private void completeTask(WmsOneClickPick task, String reason) {
        log.info("[一键领料] taskId={} 完成: {}", task.getId(), reason);
        UpdateWrapper<WmsOneClickPick> uw = new UpdateWrapper<>();
        uw.eq("id", task.getId())
          .set("status", "completed")
          .set("message", reason)
          .set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        ScheduledFuture<?> f = futures.remove(task.getId());
        if (f != null) f.cancel(false);
        runningFlags.remove(task.getId());
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private int safeInt(Integer v) { return v != null ? v : 0; }

    /**
     * 根据"是否查到物料被输送"决定下一轮延迟：
     *   releasedDelta > 0 → 料架驱动：快速再查（5 秒后）
     *   releasedDelta == 0 → 保底：按 intervalSec 间隔（分钟 × 60）
     */
    private long computeDelaySec(WmsOneClickPick task, int releasedDelta) {
        if (releasedDelta > 0) {
            log.info("[一键领料] taskId={} 查到物料被输送(releasedDelta={})，下一轮 5s 后执行",
                    task.getId(), releasedDelta);
            return 5;
        }
        long fallbackMin = task.getIntervalSec() == null ? 1 : task.getIntervalSec();
        long fallbackSec = fallbackMin * 60;
        log.info("[一键领料] taskId={} 未查到物料被输送，按保底间隔 {}s 后执行", task.getId(), fallbackSec);
        return fallbackSec;
    }

    // ============ 外部控制方法 ============

    public void startTask(WmsOneClickPick task) {
        if (!"running".equals(task.getStatus())) {
            task.setStatus("running");
            taskMapper.updateById(task);
        }
        runningFlags.computeIfAbsent(task.getId(), k -> new AtomicBoolean(true)).set(true);
        scheduleNext(task, 0);
    }

    public void pauseTask(long taskId) {
        AtomicBoolean flag = runningFlags.get(taskId);
        if (flag != null) flag.set(false);
        ScheduledFuture<?> f = futures.remove(taskId);
        if (f != null) f.cancel(false);
        UpdateWrapper<WmsOneClickPick> uw = new UpdateWrapper<>();
        uw.eq("id", taskId).set("status", "paused").set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        log.info("[一键领料] taskId={} 已暂停", taskId);
    }

    public void resumeTask(long taskId) {
        WmsOneClickPick task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus("running");
        taskMapper.updateById(task);
        runningFlags.computeIfAbsent(taskId, k -> new AtomicBoolean(true)).set(true);
        scheduleNext(task, 0);
        log.info("[一键领料] taskId={} 已恢复", taskId);
    }

    public void cancelTask(long taskId) {
        AtomicBoolean flag = runningFlags.get(taskId);
        if (flag != null) flag.set(false);
        ScheduledFuture<?> f = futures.remove(taskId);
        if (f != null) f.cancel(false);
        UpdateWrapper<WmsOneClickPick> uw = new UpdateWrapper<>();
        uw.eq("id", taskId).set("status", "cancelled").set("update_time", LocalDateTime.now());
        taskMapper.update(null, uw);
        log.info("[一键领料] taskId={} 已取消", taskId);
    }

    public boolean isEnabled() { return enabled; }
}
