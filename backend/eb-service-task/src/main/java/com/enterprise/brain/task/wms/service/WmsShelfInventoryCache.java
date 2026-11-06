package com.enterprise.brain.task.wms.service;

import com.enterprise.brain.task.wms.entity.WmsShelfSnapshot;
import com.enterprise.brain.task.wms.mapper.WmsShelfSnapshotMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * WMS 料架库存缓存（单例，定时刷新 + DB 快照持久化 + 前后代对比）
 * <p>
 * 三层设计：
 *  1. 内存缓存 —— 快速读取，两个 Scheduler 共享一份
 *  2. DB 快照表（wms_shelf_snapshot）—— 每轮刷新后持久化，generation 代次轮替
 *  3. 前后代对比 —— 检测哪些 productionNo 的物料从料架上消失了，供 Scheduler 快速响应
 * <p>
 * 轮询间隔默认 30s（可配），TTL 默认 60s。
 */
@Slf4j
@Component
public class WmsShelfInventoryCache {

    @Resource
    private WmsShelfSnapshotMapper snapshotMapper;

    @Value("${wms.shelf-cache.url:http://192.168.15.20:8081/prod-api/api/upperSystem/shelf/inventory}")
    private String shelfUrl;

    @Value("${wms.shelf-cache.timeout-ms:3000}")
    private int timeoutMs;

    /** 刷新间隔（秒）。设为 0 可禁用定时刷新（每次 get 时按需拉取） */
    @Value("${wms.shelf-cache.refresh-interval-sec:30}")
    private int refreshIntervalSec;

    /** 缓存过期时间（秒）——超过此时间视为过期，get() 时会同步拉一次 */
    @Value("${wms.shelf-cache.ttl-sec:60}")
    private int ttlSec;

    /** 是否开启 DB 快照持久化（默认 true） */
    @Value("${wms.shelf-cache.snapshot-enabled:true}")
    private boolean snapshotEnabled;

    /** DB 最多保留多少代快照（超了自动清理最老的） */
    @Value("${wms.shelf-cache.snapshot-keep-generations:10}")
    private int snapshotKeepGenerations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存数据：productionNo → materialCode → quantity */
    private volatile Map<String, Map<String, BigDecimal>> cache = Collections.emptyMap();

    /** 上次成功刷新的系统时间戳（毫秒） */
    private volatile long lastSuccessTs = 0;

    /** 上次尝试刷新的系统时间戳（毫秒，含失败） */
    private volatile long lastAttemptTs = 0;

    /** 最新快照代次 */
    private volatile int currentGeneration = 0;

    /**
     * 上一代 → 这一代 对比结果：哪些 productionNo 有物料"消失"（prev 有，curr 没）
     * key = productionNo, value = 消失的 materialCode 集合
     * 每次 doRefreshNow() 成功后更新
     */
    private volatile Map<String, Set<String>> disappearedMaterials = Collections.emptyMap();

    /**
     * 上一代 → 这一代 对比结果：哪些 productionNo 有物料"新上料"（prev 没，curr 有 / 数量增加）
     */
    private volatile Map<String, Set<String>> appearedOrIncreasedMaterials = Collections.emptyMap();

    /** 定时刷新线程池（懒加载：第一次被调用时才启动） */
    private ScheduledExecutorService refreshScheduler;

    /** 是否已启动刷新线程 */
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile RestTemplate restTemplate;

    // ======================= 初始化 =======================

    @PostConstruct
    public void init() {
        if (refreshIntervalSec <= 0) {
            log.warn("[料架缓存] refresh-interval-sec={}，禁用定时刷新（按需拉取）", refreshIntervalSec);
        } else {
            log.info("[料架缓存] 懒加载模式：首次被请求时才启动刷新线程（间隔={}s, 快照持久化={}）",
                    refreshIntervalSec, snapshotEnabled);
        }

        // 启动时从 DB 读一下最新 generation（如果有快照表的话）
        if (snapshotEnabled) {
            try {
                currentGeneration = snapshotMapper.selectMaxGeneration();
                log.info("[料架缓存] 初始化，DB 现有最新 generation={}", currentGeneration);
            } catch (Exception ex) {
                log.warn("[料架缓存] DB 快照表可能不存在，跳过初始化: {}", ex.getMessage());
                snapshotEnabled = false;
            }
        }
    }

    /** 懒加载启动刷新线程——第一次有定时领料/一键领料任务请求料架缓存时才启动 */
    private void ensureStarted() {
        if (refreshIntervalSec <= 0) return;
        if (!started.compareAndSet(false, true)) return;
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WmsShelfCache");
            t.setDaemon(true);
            return t;
        });
        // 首次调用时先立即拉一次，再按固定间隔刷新
        refreshScheduler.scheduleAtFixedRate(this::doRefreshNow, 0, refreshIntervalSec, TimeUnit.SECONDS);
        log.info("[料架缓存] 按需启动，url={}, 刷新间隔={}s, TTL={}s, 快照={}",
                shelfUrl, refreshIntervalSec, ttlSec, snapshotEnabled);
    }

    @PreDestroy
    public void destroy() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdownNow();
        }
    }

    // ======================= 对外接口 =======================

    /**
     * 获取料架库存（返回缓存，永不阻塞）。
     * 缓存过期时会触发同步刷新（最多等待 timeoutMs）。
     * 若从未刷新过或刷新失败，返回空 Map。
     */
    public Map<String, Map<String, BigDecimal>> getShelfInventory() {
        // 懒加载：首次请求时才启动后台刷新线程
        ensureStarted();
        // 还没刷过 → 同步拉一次
        if (lastSuccessTs == 0) {
            doRefreshNow();
            return cache;
        }
        // 过期了 → 同步拉一次
        long ageMs = System.currentTimeMillis() - lastSuccessTs;
        if (ageMs > ttlSec * 1000L) {
            // 防止多线程同时触发：用 lastAttemptTs 做简单防抖（2 秒内只允许一个线程拉）
            long sinceLastAttempt = System.currentTimeMillis() - lastAttemptTs;
            if (sinceLastAttempt > 2000L) {
                doRefreshNow();
            }
        }
        return cache;
    }

    /** 立即强制刷新一次（供外部主动触发，比如推完 WMS 后想尽早看到料架变化） */
    public void forceRefresh() {
        doRefreshNow();
    }

    /** 缓存是否可用（有过成功刷新） */
    public boolean isAvailable() {
        return lastSuccessTs > 0;
    }

    /** 缓存剩余有效期（毫秒），负数表示已过期 */
    public long getRemainingTtlMs() {
        if (lastSuccessTs == 0) return 0;
        return (lastSuccessTs + ttlSec * 1000L) - System.currentTimeMillis();
    }

    /** 上次成功刷新距今（毫秒） */
    public long getCacheAgeMs() {
        return lastSuccessTs == 0 ? -1 : System.currentTimeMillis() - lastSuccessTs;
    }

    /** 当前快照代次 */
    public int getCurrentGeneration() {
        return currentGeneration;
    }

    /**
     * 查某个 productionNo 是否有物料从料架上"消失"（上一代有、这一代没）。
     * Scheduler 用这个判断"该不该加速下一轮"。
     *
     * @return 消失的 materialCode 集合，空集合表示没有消失
     */
    public Set<String> getDisappearedMaterials(String productionNo) {
        if (productionNo == null) return Collections.emptySet();
        return disappearedMaterials.getOrDefault(productionNo.toUpperCase(), Collections.emptySet());
    }

    /**
     * 查某个 productionNo 是否有物料"新上料/数量增加"（上一代没、这一代有 / 数量增加）。
     *
     * @return 新上料的 materialCode 集合
     */
    public Set<String> getAppearedOrIncreasedMaterials(String productionNo) {
        if (productionNo == null) return Collections.emptySet();
        return appearedOrIncreasedMaterials.getOrDefault(productionNo.toUpperCase(), Collections.emptySet());
    }

    /**
     * 全局：上一代 → 这一代，所有有物料消失的 productionNo 集合
     */
    public Set<String> getAllProductionNosWithDisappears() {
        return disappearedMaterials.keySet();
    }

    // ======================= 内部刷新 =======================

    private synchronized void doRefreshNow() {
        lastAttemptTs = System.currentTimeMillis();
        try {
            RestTemplate rt = getRestTemplate();
            ResponseEntity<String> resp = rt.getForEntity(shelfUrl, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("[料架缓存] HTTP {} 刷新失败", resp.getStatusCode());
                return;
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
            if (!root.path("Success").asBoolean()) {
                log.warn("[料架缓存] Success=false, Message={}", root.path("Message").asText());
                return;
            }

            // 1) 解析接口 → 聚合 Map + 展开明细（带 ShelfCode）
            ParseResult parseResult = parseShelvesFull(root);

            // 2) 更新内存缓存（聚合 Map，供 Scheduler 业务查询用）
            cache = parseResult.aggregateMap;
            lastSuccessTs = System.currentTimeMillis();

            // 3) DB 快照持久化 + 前后代对比（如果启用）
            if (snapshotEnabled) {
                try {
                    persistAndCompare(parseResult);
                } catch (Exception ex) {
                    // DB 层失败不能影响内存缓存，只记日志
                    log.warn("[料架缓存] DB 快照持久化/对比失败（不影响内存缓存）: {}", ex.getMessage());
                }
            }

            log.info("[料架缓存] 刷新成功，生产订单={} 个, 明细行={}, generation={}, 耗时={}ms",
                    parseResult.aggregateMap.size(), parseResult.flatItems.size(),
                    currentGeneration, System.currentTimeMillis() - lastAttemptTs);
        } catch (Exception ex) {
            log.warn("[料架缓存] 刷新失败: {}", ex.getMessage());
        }
    }

    /**
     * 存快照到 DB + 和上一代对比。
     * 操作流程：
     *   a. generation++（下一代）
     *   b. DELETE 这一代的旧数据（幂等）
     *   c. INSERT 当前刷新的明细
     *   d. SELECT 上一代（generation - 1）做前后对比
     *   e. 清理过老的代次（如果 snapshotKeepGenerations 配置了）
     */
    private void persistAndCompare(ParseResult parseResult) {
        int prevGen = currentGeneration;
        int nextGen = prevGen + 1;

        // a) + b) 清空当前代，准备插入
        snapshotMapper.deleteByGeneration(nextGen);

        // c) 批量插入（分批防过大）
        LocalDateTime now = LocalDateTime.now();
        List<WmsShelfSnapshot> batch = new ArrayList<>(parseResult.flatItems.size());
        for (SnapshotItem item : parseResult.flatItems) {
            WmsShelfSnapshot snap = new WmsShelfSnapshot();
            snap.setGeneration(nextGen);
            snap.setShelfCode(item.shelfCode);
            snap.setProductionNo(item.productionNo);
            snap.setMaterialCode(item.materialCode);
            snap.setQuantity(item.quantity);
            snap.setCreateTime(now);
            batch.add(snap);
        }
        // MyBatis-Plus 默认逐条 insert，量大时分批（每 200 条一批）
        int batchSize = 200;
        for (int i = 0; i < batch.size(); i += batchSize) {
            int end = Math.min(i + batchSize, batch.size());
            snapshotMapper.insert(batch.get(i));
            // insertBatch 简单起见直接 for 循环调 insert；如果量大可以改成 XML 批量
            for (int j = i + 1; j < end; j++) {
                snapshotMapper.insert(batch.get(j));
            }
        }
        currentGeneration = nextGen;

        // d) 前后代对比（prevGen → nextGen）
        if (prevGen > 0) {
            compareGenerations(prevGen, nextGen);
        } else {
            // 首次刷新，没有上代对比，清空结果
            disappearedMaterials = Collections.emptyMap();
            appearedOrIncreasedMaterials = Collections.emptyMap();
            log.info("[料架缓存] 首次快照（generation={}），跳过对比", nextGen);
        }

        // e) 清理过老代次
        if (snapshotKeepGenerations > 0 && nextGen > snapshotKeepGenerations) {
            int oldestToKeep = nextGen - snapshotKeepGenerations + 1;
            // 简单逐代删除（snapshotMapper 没有范围删除接口，先用逐代）
            for (int g = 1; g < oldestToKeep; g++) {
                snapshotMapper.deleteByGeneration(g);
            }
            log.info("[料架缓存] 清理旧快照，保留 generation>={}", oldestToKeep);
        }
    }

    /**
     * 对比两代快照，算出哪些物料消失了、哪些新上料了
     */
    private void compareGenerations(int prevGen, int currGen) {
        List<WmsShelfSnapshot> prevList = snapshotMapper.selectByGeneration(prevGen);
        List<WmsShelfSnapshot> currList = snapshotMapper.selectByGeneration(currGen);

        // prevMap: productionNo -> materialCode -> quantity
        Map<String, Map<String, BigDecimal>> prevMap = groupByProdAndMat(prevList);
        Map<String, Map<String, BigDecimal>> currMap = groupByProdAndMat(currList);

        // 对比：遍历 prev，找 curr 里没的 → 消失
        //       遍历 curr，找 prev 里没的 / prev 数量更少的 → 新上料/增加
        Map<String, Set<String>> disappear = new ConcurrentHashMap<>();
        Map<String, Set<String>> appear = new ConcurrentHashMap<>();

        // 消失：prev 有，curr 没
        for (Map.Entry<String, Map<String, BigDecimal>> prodEntry : prevMap.entrySet()) {
            String prodNo = prodEntry.getKey();
            Map<String, BigDecimal> prevMats = prodEntry.getValue();
            Map<String, BigDecimal> currMats = currMap.getOrDefault(prodNo, Collections.emptyMap());

            Set<String> disappearedSet = new HashSet<>();
            for (Map.Entry<String, BigDecimal> matEntry : prevMats.entrySet()) {
                String mc = matEntry.getKey();
                BigDecimal prevQty = matEntry.getValue();
                BigDecimal currQty = currMats.get(mc);
                if (currQty == null || currQty.compareTo(prevQty) < 0) {
                    // 消失 OR 数量减少：都算"料架上少了"
                    disappearedSet.add(mc);
                }
            }
            if (!disappearedSet.isEmpty()) {
                disappear.put(prodNo, disappearedSet);
            }
        }

        // 新上料/增加：curr 有，prev 没 / currQty > prevQty
        for (Map.Entry<String, Map<String, BigDecimal>> prodEntry : currMap.entrySet()) {
            String prodNo = prodEntry.getKey();
            Map<String, BigDecimal> currMats = prodEntry.getValue();
            Map<String, BigDecimal> prevMats = prevMap.getOrDefault(prodNo, Collections.emptyMap());

            Set<String> appearedSet = new HashSet<>();
            for (Map.Entry<String, BigDecimal> matEntry : currMats.entrySet()) {
                String mc = matEntry.getKey();
                BigDecimal currQty = matEntry.getValue();
                BigDecimal prevQty = prevMats.get(mc);
                if (prevQty == null || currQty.compareTo(prevQty) > 0) {
                    appearedSet.add(mc);
                }
            }
            if (!appearedSet.isEmpty()) {
                appear.put(prodNo, appearedSet);
            }
        }

        disappearedMaterials = disappear;
        appearedOrIncreasedMaterials = appear;

        // 日志汇总
        int totalDisappearProds = disappear.size();
        int totalDisappearMats = disappear.values().stream().mapToInt(Set::size).sum();
        int totalAppearProds = appear.size();
        int totalAppearMats = appear.values().stream().mapToInt(Set::size).sum();
        log.info("[料架缓存] 前后代对比 generation={}→{}: 消失 {} 个生产订单/{} 种物料, 新上料 {} 个生产订单/{} 种物料",
                prevGen, currGen, totalDisappearProds, totalDisappearMats,
                totalAppearProds, totalAppearMats);

        // 有消失 → 重点列一下（方便运维看日志）
        if (!disappear.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : disappear.entrySet()) {
                log.info("[料架缓存] ▶ 料架释放: productionNo={}, 消失物料={}", e.getKey(), e.getValue());
            }
        }
    }

    private Map<String, Map<String, BigDecimal>> groupByProdAndMat(List<WmsShelfSnapshot> list) {
        Map<String, Map<String, BigDecimal>> map = new HashMap<>();
        for (WmsShelfSnapshot s : list) {
            map.computeIfAbsent(s.getProductionNo(), k -> new HashMap<>())
               .merge(s.getMaterialCode(), s.getQuantity() != null ? s.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }
        return map;
    }

    // ======================= 解析接口 =======================

    /** 解析结果：聚合 Map + 带 ShelfCode 的展开明细 */
    private static class ParseResult {
        /** productionNo → materialCode → quantity（聚合，供业务查） */
        final Map<String, Map<String, BigDecimal>> aggregateMap;
        /** 展开的明细项（带 ShelfCode，供 DB 快照存） */
        final List<SnapshotItem> flatItems;

        ParseResult(Map<String, Map<String, BigDecimal>> aggregateMap, List<SnapshotItem> flatItems) {
            this.aggregateMap = aggregateMap;
            this.flatItems = flatItems;
        }
    }

    /** 单条快照明细（接口 item + 所属 ShelfCode） */
    private static class SnapshotItem {
        final String shelfCode;
        final String productionNo;
        final String materialCode;
        final BigDecimal quantity;

        SnapshotItem(String shelfCode, String productionNo, String materialCode, BigDecimal quantity) {
            this.shelfCode = shelfCode;
            this.productionNo = productionNo;
            this.materialCode = materialCode;
            this.quantity = quantity;
        }
    }

    /**
     * 解析 WMS shelf/inventory 接口返回。
     * 同时产出：
     *  - 聚合 Map（供 Scheduler 查"某生产订单某物料料架上有多少"）
     *  - 展开明细（带 ShelfCode，供 DB 快照存）
     */
    private ParseResult parseShelvesFull(JsonNode root) {
        JsonNode shelves = root.path("Data").path("Shelves");
        if (!shelves.isArray()) {
            return new ParseResult(Collections.emptyMap(), Collections.emptyList());
        }

        Map<String, Map<String, BigDecimal>> aggregate = new HashMap<>();
        List<SnapshotItem> flat = new ArrayList<>();

        for (JsonNode shelf : shelves) {
            String shelfCode = shelf.path("ShelfCode").asText().toUpperCase();
            JsonNode items = shelf.path("Items");
            if (!items.isArray()) continue;

            for (JsonNode item : items) {
                String prodNo = item.path("ProdctionNo").asText().toUpperCase();
                String mc = item.path("MaterialCode").asText().toUpperCase();
                String qtyStr = item.path("Quantity").asText("0");
                BigDecimal qty;
                try {
                    qty = new BigDecimal(qtyStr);
                } catch (NumberFormatException e) {
                    qty = BigDecimal.ZERO;
                }
                if (prodNo.isBlank() || mc.isBlank()) continue;

                // 聚合（同一 prodNo + materialCode 可能跨多个 Shelf，累加）
                aggregate.computeIfAbsent(prodNo, k -> new HashMap<>())
                         .merge(mc, qty, BigDecimal::add);

                // 展开（每条 ShelfCode + prodNo + materialCode 一行）
                flat.add(new SnapshotItem(shelfCode, prodNo, mc, qty));
            }
        }
        return new ParseResult(aggregate, flat);
    }

    private RestTemplate getRestTemplate() {
        if (restTemplate == null) {
            synchronized (this) {
                if (restTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(timeoutMs);
                    factory.setReadTimeout(timeoutMs);
                    restTemplate = new RestTemplate(factory);
                }
            }
        }
        return restTemplate;
    }
}
