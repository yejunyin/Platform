package com.enterprise.brain.task.wage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.wage.dto.WageMoRowVO;
import com.enterprise.brain.task.wage.dto.WagePpbomRowVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 工资核算模块专属金蝶查询服务
 * <p>负责调用金蝶标准接口查询《生产用料清单》(PRD_PPBOM) 等单据。
 * 底层 HTTP 调用与会话管理复用 task.service.KingdeeService 的通用方法。</p>
 */
@Slf4j
@Service
public class KingdeeWageService {

    /** 默认查询近 N 个月创建的用料清单（避免无关键词时拉全量） */
    private static final int DEFAULT_LOOKBACK_MONTHS = 1;
    /** 无关键词时的默认查询上限 */
    private static final int DEFAULT_LIMIT = 2000;

    @Resource
    private KingdeeService kingdeeService;

    /**
     * 查询金蝶《生产用料清单》(PRD_PPBOM)：子项物料名称包含任一关键词 + 创建日期 >= 起始日期
     *
     * @param keywords  关键词列表，子项物料名称(FMaterialID2.fname) LIKE 匹配（OR 组合）；为空则不加关键词条件
     * @param startDate 创建日期起始（FCreateDate >= 该日期），格式 yyyy-MM-dd；为空则默认近 {@link #DEFAULT_LOOKBACK_MONTHS} 个月
     * @param limit     最大返回条数，空或 <=0 时取默认值
     * @return 匹配的用料清单行（按创建日期降序）
     */
    public List<WagePpbomRowVO> queryPpbomByKeyword(List<String> keywords, String startDate, Integer limit) {
        // 1) 日期兜底（Java 层算好字符串，避免金蝶 FilterString 不支持 DATEADD 等 SQL 函数）
        String effectiveStart = startDate;
        if (effectiveStart == null || effectiveStart.isEmpty()) {
            effectiveStart = LocalDate.now().minusMonths(DEFAULT_LOOKBACK_MONTHS)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;

        // 2) 构造 FilterString（FCreateDate 为金蝶 PRD_PPBOM 标准创建日期字段）
        StringBuilder filter = new StringBuilder();
        if (effectiveStart != null && !effectiveStart.isEmpty()) {
            filter.append("FCreateDate >= '").append(effectiveStart).append("'");
        }
        if (keywords != null && !keywords.isEmpty()) {
            if (filter.length() > 0) filter.append(" and ");
            filter.append('(');
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) filter.append(" or ");
                filter.append("FMaterialID2.fname like '%")
                        .append(keywords.get(i).replace("'", "''"))
                        .append("%'");
            }
            filter.append(')');
        }

        log.info("[工资核算-金蝶] queryPpbomByKeyword filter={}", filter);

        // 3) FieldKeys 顺序必须稳定，对应下面的 row.get(index)
        String fieldKeys = "FBillNo,FMOBillNO,FMaterialID.fnumber,FMaterialID.fname," +
                "FMaterialID2.fnumber,FMaterialID2.fname,FMaterialModel1,FNeedQty2,FUnitID2.fname,FCreateDate";

        List<JsonNode> rows = kingdeeService.executeBillQuery("PRD_PPBOM", fieldKeys, filter.toString(),
                "FCreateDate desc", effectiveLimit);

        // 4) 行级解析（复用 task.KingdeeService 的 textOf/decimalOf）
        List<WagePpbomRowVO> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 10) continue;
            WagePpbomRowVO vo = new WagePpbomRowVO();
            vo.setPpbomBillNo(kingdeeService.textOf(row.get(0)));
            vo.setMoBillNo(kingdeeService.textOf(row.get(1)));
            vo.setProductCode(kingdeeService.textOf(row.get(2)));
            vo.setProductName(kingdeeService.textOf(row.get(3)));
            vo.setMaterialCode(kingdeeService.textOf(row.get(4)));
            vo.setMaterialName(kingdeeService.textOf(row.get(5)));
            vo.setModel(kingdeeService.textOf(row.get(6)));
            vo.setNeedQty(kingdeeService.decimalOf(row.get(7)));
            vo.setUnitName(kingdeeService.textOf(row.get(8)));
            vo.setCreateDate(kingdeeService.textOf(row.get(9)));
            result.add(vo);
        }
        return result;
    }

    /**
     * 查询金蝶《生产订单》(PRD_MO) —— 近 N 个月创建的全部订单
     *
     * @param monthsLookback 回溯月数，默认 12
     * @param limit          最大返回条数，默认 5000
     */
    public List<WageMoRowVO> queryMoRecent(Integer monthsLookback, Integer limit) {
        int months = (monthsLookback == null || monthsLookback <= 0) ? 1 : monthsLookback;
        int max = (limit == null || limit <= 0) ? 5000 : limit;

        String startDate = LocalDate.now().minusMonths(months)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // PRD_MO 没有 FCreateDate 标准字段，但 FPlanStartDate / FBillDate 常用；
        // 保险起见用 FBillDate >= 回溯日期
        String filter = "FBillDate >= '" + startDate + "'";

        log.info("[工资核算-金蝶] queryMoRecent filter={} limit={}", filter, max);

        String fieldKeys = "FBillNo,FMaterialID.fnumber,FMaterialID.fname," +
                "FQty,FYieldQty,FStatus,FWorkShopID.fname,FPlanStartDate,FPlanFinishDate,FBillDate";

        List<JsonNode> rows = kingdeeService.executeBillQuery("PRD_MO", fieldKeys, filter,
                "FBillNo desc", max);

        List<WageMoRowVO> result = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 9) continue;
            WageMoRowVO vo = new WageMoRowVO();
            vo.setMoBillNo(kingdeeService.textOf(row.get(0)));
            vo.setProductCode(kingdeeService.textOf(row.get(1)));
            vo.setProductName(kingdeeService.textOf(row.get(2)));
            vo.setQty(kingdeeService.decimalOf(row.get(3)));
            vo.setYieldQty(kingdeeService.decimalOf(row.get(4)));
            vo.setStatus(kingdeeService.textOf(row.get(5)));
            vo.setWorkshop(kingdeeService.textOf(row.get(6)));
            vo.setPlanStartTime(kingdeeService.textOf(row.get(7)));
            vo.setPlanFinishTime(kingdeeService.textOf(row.get(8)));
            result.add(vo);
        }
        log.info("[工资核算-金蝶] queryMoRecent 返回 {} 张生产订单", result.size());
        return result;
    }

    /**
     * 按生产订单号批量查询金蝶《生产订单》(PRD_MO) 单据信息
     * <p>用用料清单里的 task_no（FMOBillNO）作为 PRD_MO.FBillNo 去查订单头。</p>
     *
     * <p>注：不查询 FPlanStartDate/FPlanFinishDate（金蝶计划时间），
     * 工资核算模块的 startTime/finishTime 由本地 start()/done() 方法用 now() 记录真实开工/完工时间。</p>
     *
     * @param moBillNos 生产订单号（PRD_MO.FBillNo）
     * @return PRD_MO 订单行（去重后，每个单号一条）
     */
    public List<WageMoRowVO> queryMoByBillNos(List<String> moBillNos) {
        if (moBillNos == null || moBillNos.isEmpty()) return new ArrayList<>();

        // 只查需要的字段：单号 / 产品编码 / 产品名称 / 产品数量 / 完工入库数 / 状态 / 生产车间
        // 不查计划开工/完工时间（FPlanStartDate/FPlanFinishDate），本地 startTime/finishTime 由 start()/done() 记录
        String fieldKeys = "FBillNo,FMaterialID.fnumber,FMaterialID.fname," +
                "FQty,FYieldQty,FStatus,FWorkShopID.fname";

        // 分批查询避免 FilterString 过长：每批最多 500 个单号
        int batchSize = 500;
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < moBillNos.size(); i += batchSize) {
            batches.add(moBillNos.subList(i, Math.min(i + batchSize, moBillNos.size())));
        }
        List<WageMoRowVO> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (List<String> batch : batches) {
            StringBuilder f = new StringBuilder("FBillNo in (");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) f.append(',');
                f.append("'").append(batch.get(i).replace("'", "''")).append("'");
            }
            f.append(')');
            List<JsonNode> rows = kingdeeService.executeBillQuery("PRD_MO", fieldKeys, f.toString(),
                    "FBillNo asc", batch.size());
            // 调试：打印本批前 2 条原始字段
            int debugN = Math.min(2, rows.size());
            for (int i = 0; i < debugN; i++) {
                JsonNode r = rows.get(i);
                if (!r.isArray() || r.size() < 7) continue;
                log.info("[工资核算-金蝶] PRD_MO row[{}] FBillNo={} FQty={} FYieldQty={} FStatus={} FWorkShopID.fname={}",
                        i, kingdeeService.textOf(r.get(0)), kingdeeService.decimalOf(r.get(3)),
                        kingdeeService.decimalOf(r.get(4)), kingdeeService.textOf(r.get(5)),
                        kingdeeService.textOf(r.get(6)));
            }
            for (JsonNode row : rows) {
                if (!row.isArray() || row.size() < 7) continue;
                String billNo = kingdeeService.textOf(row.get(0));
                if (billNo == null || seen.contains(billNo)) continue;
                seen.add(billNo);
                WageMoRowVO vo = new WageMoRowVO();
                vo.setMoBillNo(billNo);
                vo.setProductCode(kingdeeService.textOf(row.get(1)));
                vo.setProductName(kingdeeService.textOf(row.get(2)));
                vo.setQty(kingdeeService.decimalOf(row.get(3)));
                vo.setYieldQty(kingdeeService.decimalOf(row.get(4)));
                vo.setStatus(kingdeeService.textOf(row.get(5)));
                vo.setWorkshop(kingdeeService.textOf(row.get(6)));
                result.add(vo);
            }
        }
        log.info("[工资核算-金蝶] PRD_MO.queryByBillNos 输入 {} 个单号，返回 {} 条订单", moBillNos.size(), result.size());
        return result;
    }
}
