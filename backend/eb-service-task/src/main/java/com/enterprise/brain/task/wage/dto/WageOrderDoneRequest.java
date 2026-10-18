package com.enterprise.brain.task.wage.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 确认完工
 */
@Data
public class WageOrderDoneRequest {

    /** 完工入库数（人工填写，后续可由金蝶入库单回写） */
    private BigDecimal stockInQty;

    /** 完工确认人（当前登录用户姓名） */
    private String finishUser;
}
