package com.enterprise.brain.task.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WMS 料架库存快照（wms_shelf_snapshot）
 * <p>
 * 存 shelf/inventory 接口每次轮询返回的数据，用于前后代对比：
 *  generation 字段轮替（第N次刷新存 generation=N），对比 generation=N 和 N-1 的差异，
 *  检测哪些物料从料架上消失了 → 触发正在跑的 Scheduler 快速响应。
 */
@Data
@TableName("wms_shelf_snapshot")
public class WmsShelfSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 快照代次（每次刷新+1，用于前后对比） */
    private Integer generation;

    /** 货架编码 */
    private String shelfCode;

    /** 生产订单号（ProdctionNo，接口原始拼写） */
    private String productionNo;

    /** 物料编码 */
    private String materialCode;

    /** 料架上该物料数量 */
    private BigDecimal quantity;

    /** 记录创建时间 */
    private LocalDateTime createTime;
}
