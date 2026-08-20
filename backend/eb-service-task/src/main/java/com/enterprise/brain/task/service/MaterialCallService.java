package com.enterprise.brain.task.service;

import com.enterprise.brain.task.dto.request.MaterialCallCreateRequest;
import com.enterprise.brain.task.dto.request.MaterialPrepareRequest;
import com.enterprise.brain.task.dto.response.MaterialAlertDTO;
import com.enterprise.brain.task.dto.response.MaterialCallRecordDTO;
import com.enterprise.brain.task.dto.response.MaterialCallTaskDTO;
import com.enterprise.brain.task.dto.response.MaterialDetailDTO;

import java.util.List;

/**
 * 物料呼叫系统服务
 */
public interface MaterialCallService {

    /**
     * 拉取 MES 生产任务列表
     */
    List<MaterialCallTaskDTO> listTasks();

    /**
     * 查询备料明细
     * @param taskId 任务单号，可空（空=查全部）
     */
    List<MaterialDetailDTO> listMaterials(String taskId);

    /**
     * 查询叫料记录
     * @param status 状态：all/pending/delivering/delivered
     */
    List<MaterialCallRecordDTO> listCalls(String status);

    /**
     * 查询预警
     * @param status 状态：all/active/resolved
     */
    List<MaterialAlertDTO> listAlerts(String status);

    /**
     * 同步指定任务的备料明细：
     * <ul>
     *   <li>BOMflag=1：直接从 mes_dwd_material_detail 查询返回；</li>
     *   <li>BOMflag=0：调用金蝶 ERP 拉取生产用料清单，写入 mes_dwd_material_detail 并把 BOMflag 置 1，再返回。</li>
     * </ul>
     * @param taskId 任务单号（mes_dwd_productOrder.pcode）
     */
    List<MaterialDetailDTO> syncMaterials(String taskId);

    /**
     * 备料（标记备料中）：更新 mes_dwd_material_detail 状态为 preparing，并记录备料人/时间
     * @param id 备料明细主键
     * @return 更新后的备料明细
     */
    MaterialDetailDTO startPrepare(Integer id);

    /**
     * 备齐：累加已备数量，按是否满足需求将状态置为 ready / shortage，记录备料人/时间/备注
     * @param id 备料明细主键
     * @param request 备料数量与备料人
     * @return 更新后的备料明细
     */
    MaterialDetailDTO prepareMaterial(Integer id, MaterialPrepareRequest request);

    /**
     * 叫料：写入 mes_dwd_material_call 一条 pending 记录，并将对应备料明细状态置为 shortage、生成缺料预警
     * @param request 叫料信息
     * @return 创建的叫料记录（含生成的主键 id）
     */
    MaterialCallRecordDTO createCall(MaterialCallCreateRequest request);

    /**
     * 响应叫料：将叫料记录状态置为 delivering，记录响应人/响应时间
     * @param id 叫料记录主键
     * @param responder 响应人
     * @return 更新后的叫料记录
     */
    MaterialCallRecordDTO respondCall(Integer id, String responder);

    /**
     * 确认送达：将叫料记录状态置为 delivered，记录送达时间；同时累加对应备料明细的已备数量、
     * 按是否满足需求更新其状态（ready / shortage），并将对应缺料预警置为 resolved
     * @param id 叫料记录主键
     * @return 更新后的叫料记录
     */
    MaterialCallRecordDTO deliverCall(Integer id);
}
