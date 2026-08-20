package com.enterprise.brain.task.service.impl;

import com.enterprise.brain.task.dto.request.MaterialCallCreateRequest;
import com.enterprise.brain.task.dto.request.MaterialPrepareRequest;
import com.enterprise.brain.task.dto.response.MaterialAlertDTO;
import com.enterprise.brain.task.dto.response.MaterialCallRecordDTO;
import com.enterprise.brain.task.dto.response.MaterialCallTaskDTO;
import com.enterprise.brain.task.dto.response.MaterialDetailDTO;
import com.enterprise.brain.task.entity.MesMaterialAlert;
import com.enterprise.brain.task.entity.MesMaterialCall;
import com.enterprise.brain.task.entity.MesMaterialDetail;
import com.enterprise.brain.task.entity.MesProductOrder;
import com.enterprise.brain.task.mapper.MesMaterialAlertMapper;
import com.enterprise.brain.task.mapper.MesMaterialCallMapper;
import com.enterprise.brain.task.mapper.MesMaterialDetailMapper;
import com.enterprise.brain.task.mapper.MesProductOrderMapper;
import com.enterprise.brain.task.service.KingdeeService;
import com.enterprise.brain.task.service.MaterialCallService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 物料呼叫系统服务实现
 */
@Slf4j
@Service
public class MaterialCallServiceImpl implements MaterialCallService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 默认备料人（前端 currentUser = '备料员'，此处保持一致以便回填） */
    private static final String DEFAULT_PREPARER = "备料员";
    /** 默认响应人（前端在响应时如未填会回填 '仓管员'） */
    private static final String DEFAULT_RESPONDER = "仓管员";

    @Resource
    private MesProductOrderMapper mesProductOrderMapper;
    @Resource
    private MesMaterialDetailMapper mesMaterialDetailMapper;
    @Resource
    private MesMaterialCallMapper mesMaterialCallMapper;
    @Resource
    private MesMaterialAlertMapper mesMaterialAlertMapper;
    @Resource
    private KingdeeService kingdeeService;

    @Override
    public List<MaterialCallTaskDTO> listTasks() {
        List<MesProductOrder> orders = mesProductOrderMapper.selectAllOrders();
        if (orders == null || orders.isEmpty()) return Collections.emptyList();
        return orders.stream().map(this::toTaskDTO).collect(Collectors.toList());
    }

    @Override
    public List<MaterialDetailDTO> listMaterials(String taskId) {
        List<MesMaterialDetail> list = mesMaterialDetailMapper.selectByTaskId(taskId);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream().map(this::toDetailDTO).collect(Collectors.toList());
    }

    @Override
    public List<MaterialCallRecordDTO> listCalls(String status) {
        List<MesMaterialCall> list = mesMaterialCallMapper.selectByStatus(status);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream().map(this::toCallDTO).collect(Collectors.toList());
    }

    @Override
    public List<MaterialAlertDTO> listAlerts(String status) {
        List<MesMaterialAlert> list = mesMaterialAlertMapper.selectByStatus(status);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list.stream().map(this::toAlertDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MaterialDetailDTO> syncMaterials(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("任务单号 taskId 不能为空");
        }

        MesProductOrder order = mesProductOrderMapper.selectByPcode(taskId);
        if (order == null) {
            throw new IllegalArgumentException("任务单号 " + taskId + " 在 mes_dwd_productOrder 中不存在");
        }

        boolean bomSynced = "1".equals(order.getBomflag());
        if (bomSynced) {
            log.info("[MaterialCall] 任务 {} BOMflag=1，直接返回 mes_dwd_material_detail 数据", taskId);
            return listMaterials(taskId);
        }

        String materialid = order.getMaterialid();
        log.info("[MaterialCall] 任务 {} BOMflag=0，开始从金蝶 ERP 拉取生产用料清单（materialid={}）",
                taskId, materialid);

        // 1) 金蝶登录
        KingdeeService.LoginResult loginResult = kingdeeService.login();

        // 2) 调用 ExecuteBillQuery 拉取 BOM
        List<MesMaterialDetail> details = kingdeeService.queryBom(taskId, materialid, loginResult);
        log.info("[MaterialCall] 任务 {} 从金蝶拉取到 {} 条备料明细", taskId, details.size());

        // 3) 清空旧数据 + 批量写入新数据
        mesMaterialDetailMapper.deleteByTaskId(taskId);
        for (MesMaterialDetail d : details) {
            mesMaterialDetailMapper.insert(d);
        }

        // 4) 把 mes_dwd_productOrder.BOMflag 置 1
        mesProductOrderMapper.markBomSynced(taskId);

        // 5) 返回最新数据
        return listMaterials(taskId);
    }

    // ===== 业务操作 =====

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialDetailDTO startPrepare(Integer id) {
        MesMaterialDetail m = mesMaterialDetailMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("备料明细 id=" + id + " 不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        mesMaterialDetailMapper.updateStatus(id, "preparing", DEFAULT_PREPARER, now);
        log.info("[MaterialCall] 备料中: id={}, taskId={}, materialCode={}", id, m.getTaskId(), m.getMaterialCode());
        return toDetailDTO(mesMaterialDetailMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialDetailDTO prepareMaterial(Integer id, MaterialPrepareRequest request) {
        MesMaterialDetail m = mesMaterialDetailMapper.selectById(id);
        if (m == null) {
            throw new IllegalArgumentException("备料明细 id=" + id + " 不存在");
        }
        int totalPrepared = (m.getPreparedQty() == null ? 0 : m.getPreparedQty()) + request.getQty();
        int required = m.getRequiredQty() == null ? 0 : m.getRequiredQty();
        String unit = m.getUnit() == null ? "" : m.getUnit();
        boolean meet = totalPrepared >= required;
        String status = meet ? "ready" : "shortage";
        String remark = meet ? null : "备料" + totalPrepared + "/" + required + "，缺口" + (required - totalPrepared) + unit;

        LocalDateTime now = LocalDateTime.now();
        mesMaterialDetailMapper.updatePrepare(id, totalPrepared, status, request.getPreparer(), now, remark);
        log.info("[MaterialCall] 备齐: id={}, preparedQty={} -> {}, status={}", id, m.getPreparedQty(), totalPrepared, status);
        return toDetailDTO(mesMaterialDetailMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialCallRecordDTO createCall(MaterialCallCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        boolean urgent = "urgent".equals(request.getCallType());

        // 1) 写入叫料记录（status=pending）
        MesMaterialCall call = new MesMaterialCall();
        call.setTaskId(request.getTaskId());
        call.setMaterialCode(request.getMaterialCode());
        call.setMaterialName(request.getMaterialName());
        call.setRequiredQty(request.getRequiredQty());
        call.setCallQty(request.getCallQty());
        call.setCallType(request.getCallType());
        call.setCaller(request.getCaller());
        call.setCallTime(now);
        call.setStatus("pending");
        call.setRemark(request.getRemark());
        mesMaterialCallMapper.insert(call);

        // 2) 对应备料明细置为 shortage（仅改状态与备注，不动 prepare_time / preparer，与前端 addCall 一致）
        MesMaterialDetail m = mesMaterialDetailMapper.selectByTaskIdAndMaterialCode(request.getTaskId(), request.getMaterialCode());
        if (m != null) {
            String unit = m.getUnit() == null ? "" : m.getUnit();
            String remark = "已叫料 " + request.getCallQty() + " " + unit;
            mesMaterialDetailMapper.updateStatusAndRemark(m.getId(), "shortage", remark);
        }

        // 3) 生成缺料预警
        MesMaterialAlert alert = new MesMaterialAlert();
        alert.setTaskId(request.getTaskId());
        alert.setType("shortage");
        alert.setLevel(urgent ? "danger" : "warning");
        alert.setMessage("物料 [" + request.getMaterialName() + "] 叫料 " + request.getCallQty()
                + "，" + (urgent ? "紧急" : "普通") + "叫料");
        alert.setStatus("active");
        alert.setCreatedAt(now);
        mesMaterialAlertMapper.insert(alert);

        log.info("[MaterialCall] 叫料: callId={}, taskId={}, materialCode={}, callQty={}, type={}",
                call.getId(), request.getTaskId(), request.getMaterialCode(), request.getCallQty(), request.getCallType());
        return toCallDTO(mesMaterialCallMapper.selectById(call.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialCallRecordDTO respondCall(Integer id, String responder) {
        MesMaterialCall call = mesMaterialCallMapper.selectById(id);
        if (call == null) {
            throw new IllegalArgumentException("叫料记录 id=" + id + " 不存在");
        }
        if (!"pending".equals(call.getStatus())) {
            throw new IllegalStateException("叫料记录 id=" + id + " 当前状态为 " + call.getStatus() + "，无法响应");
        }
        String responderName = (responder == null || responder.isEmpty()) ? DEFAULT_RESPONDER : responder;
        LocalDateTime now = LocalDateTime.now();
        mesMaterialCallMapper.updateRespond(id, "delivering", responderName, now);
        log.info("[MaterialCall] 响应: callId={}, responder={}", id, responderName);
        return toCallDTO(mesMaterialCallMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialCallRecordDTO deliverCall(Integer id) {
        MesMaterialCall call = mesMaterialCallMapper.selectById(id);
        if (call == null) {
            throw new IllegalArgumentException("叫料记录 id=" + id + " 不存在");
        }
        if (!"delivering".equals(call.getStatus())) {
            throw new IllegalStateException("叫料记录 id=" + id + " 当前状态为 " + call.getStatus() + "，无法确认送达");
        }
        LocalDateTime now = LocalDateTime.now();

        // 1) 叫料记录置 delivered
        mesMaterialCallMapper.updateDeliver(id, "delivered", now);

        // 2) 累加对应备料明细已备数量并更新状态/备注
        MesMaterialDetail m = mesMaterialDetailMapper.selectByTaskIdAndMaterialCode(call.getTaskId(), call.getMaterialCode());
        if (m != null) {
            int newPrepared = (m.getPreparedQty() == null ? 0 : m.getPreparedQty())
                    + (call.getCallQty() == null ? 0 : call.getCallQty());
            int required = m.getRequiredQty() == null ? 0 : m.getRequiredQty();
            String unit = m.getUnit() == null ? "" : m.getUnit();
            boolean meet = newPrepared >= required;
            String newStatus = meet ? "ready" : "shortage";
            String newRemark = meet ? null : "已送达" + call.getCallQty() + "，仍缺" + (required - newPrepared) + unit;
            mesMaterialDetailMapper.updateAfterDeliver(m.getId(), newPrepared, newStatus, now, newRemark);
        }

        // 3) 对应缺料预警置 resolved
        mesMaterialAlertMapper.resolveByTaskAndMaterial(call.getTaskId(), call.getMaterialName());

        log.info("[MaterialCall] 确认送达: callId={}, taskId={}, materialCode={}", id, call.getTaskId(), call.getMaterialCode());
        return toCallDTO(mesMaterialCallMapper.selectById(id));
    }

    // ===== 转换函数 =====
    private MaterialCallTaskDTO toTaskDTO(MesProductOrder o) {
        MaterialCallTaskDTO dto = new MaterialCallTaskDTO();
        dto.setId(o.getPcode());
        dto.setOrderNo(o.getOrdercode());
        dto.setProductCode(o.getMaterialid());
        dto.setProductName(o.getMaterialname());
        dto.setQuantity(o.getTotal());
        dto.setUnit(o.getUnit() == null ? "" : o.getUnit());
        dto.setWorkshop(o.getDept());
        dto.setLine(o.getMachcode());
        dto.setPlannedStart(formatTime(o.getStarttime()));
        dto.setPlannedEnd(null);
        dto.setStatus("pending");
        dto.setSchedulepriority(o.getSchedulepriority());
        dto.setSpec(o.getSpec());
        dto.setStaffname(o.getStaffname());
        return dto;
    }

    private MaterialDetailDTO toDetailDTO(MesMaterialDetail m) {
        MaterialDetailDTO dto = new MaterialDetailDTO();
        dto.setId(m.getId());
        dto.setTaskId(m.getTaskId());
        dto.setMaterialCode(m.getMaterialCode());
        dto.setMaterialName(m.getMaterialName());
        dto.setSpecification(m.getSpecification());
        dto.setUnit(m.getUnit() == null ? "" : m.getUnit());
        dto.setRequiredQty(m.getRequiredQty());
        dto.setAvailableQty(m.getAvailableQty());
        dto.setPreparedQty(m.getPreparedQty());
        dto.setStorageLocation(m.getStorageLocation());
        dto.setStatus(m.getStatus());
        dto.setPreparer(m.getPreparer());
        dto.setPrepareTime(formatTime(m.getPrepareTime()));
        dto.setRemark(m.getRemark());
        return dto;
    }

    private MaterialCallRecordDTO toCallDTO(MesMaterialCall c) {
        MaterialCallRecordDTO dto = new MaterialCallRecordDTO();
        dto.setId(c.getId());
        dto.setTaskId(c.getTaskId());
        dto.setMaterialCode(c.getMaterialCode());
        dto.setMaterialName(c.getMaterialName());
        dto.setRequiredQty(c.getRequiredQty());
        dto.setCallQty(c.getCallQty());
        dto.setCallType(c.getCallType());
        dto.setCaller(c.getCaller());
        dto.setCallTime(formatTime(c.getCallTime()));
        dto.setStatus(c.getStatus());
        dto.setResponder(c.getResponder());
        dto.setResponseTime(formatTime(c.getResponseTime()));
        dto.setDeliverTime(formatTime(c.getDeliverTime()));
        dto.setRemark(c.getRemark());
        return dto;
    }

    private MaterialAlertDTO toAlertDTO(MesMaterialAlert a) {
        MaterialAlertDTO dto = new MaterialAlertDTO();
        dto.setId(a.getId());
        dto.setTaskId(a.getTaskId());
        dto.setType(a.getType());
        dto.setLevel(a.getLevel());
        dto.setMessage(a.getMessage());
        dto.setStatus(a.getStatus());
        dto.setCreatedAt(formatTime(a.getCreatedAt()));
        return dto;
    }

    private String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }
}
