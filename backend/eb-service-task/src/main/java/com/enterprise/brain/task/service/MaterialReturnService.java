package com.enterprise.brain.task.service;

import com.enterprise.brain.task.dto.request.MaterialAuditRequest;
import com.enterprise.brain.task.dto.request.MaterialSubmitRequest;
import com.enterprise.brain.task.dto.response.AuditListItemDTO;
import com.enterprise.brain.task.dto.response.MaterialApplicationDTO;
import com.enterprise.brain.task.dto.response.MaterialCallOpResultDTO;
import com.enterprise.brain.task.dto.response.MaterialResult;
import com.enterprise.brain.task.dto.response.OrderInfoDTO;
import com.enterprise.brain.task.dto.response.OrderMaterialsDTO;
import com.enterprise.brain.task.dto.response.QcStaffDTO;

import java.util.List;
import java.util.Map;

/**
 * 补退料（生产补料/生产退料）服务
 * <p>对应《补退料后端接口文档》11个接口 + FIFO批次匹配 + 金蝶退料单生成 + WMS出库申请流水线。</p>
 */
public interface MaterialReturnService {

    /** 接口1：获取生产订单信息（仅已下达/生产中允许补料） */
    OrderInfoDTO getOrder(String orderCode, String username);

    /** 接口2：获取订单用料清单（应发/已领/差异/可用库存，按订单分组） */
    List<OrderMaterialsDTO> getOrderMaterials(List<String> orderCodes, String username);

    /** 接口3：获取补料原因字典 */
    List<Map<String, Object>> getReasons(Integer reasonType);

    /** 接口4：获取质检员列表 */
    List<QcStaffDTO> getQcStaffList(String username);

    /** 接口5：提交补退料申请（服务端二次校验数量） */
    MaterialCallOpResultDTO submit(MaterialSubmitRequest request, String username);

    /** 接口6：获取我的申请列表 */
    List<MaterialApplicationDTO> getMyApplications(String username, String status,
                                                   String startTime, String endTime);

    /** 接口7：获取申请单详情（含批次匹配结果） */
    MaterialApplicationDTO getApplicationDetail(String id, String username);

    /** 接口8：获取质检待审列表 */
    List<AuditListItemDTO> getAuditList(String username);

    /** 接口9：质检审核（驳回 / 通过触发FIFO匹配+ERP+WMS流水线；库存不足返回status=2，data为{requestQty,matchQty}） */
    MaterialResult<?> audit(MaterialAuditRequest request, String username);

    /** 接口10：重试生成ERP退料单（状态31） */
    MaterialCallOpResultDTO retryErpOrder(String id, String username);

    /** 接口11：重试生成WMS出库申请（状态41） */
    MaterialCallOpResultDTO retryWmsOrder(String id, String username);
}
