package com.enterprise.brain.task.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.wage.dto.WageOrderVO;
import com.enterprise.brain.task.wage.entity.WageOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WageOrderMapper extends BaseMapper<WageOrder> {

    /**
     * 任务单分页列表（只含单据头，状态/组别等在子表）
     * <p>筛选只保留单据头字段，物料/状态过滤由前端展开子表后处理</p>
     */
    @Select("<script>" +
            "SELECT o.id, o.task_no, o.feed_no, o.product_name, o.sub_material_code AS materialCode, " +
            "       o.product_qty, o.workshop, o.sync_time, o.create_time " +
            "FROM wage_order o " +
            "WHERE o.deleted = 0 " +
            "  AND EXISTS (SELECT 1 FROM wage_material m " +
            "              WHERE m.task_no = o.task_no AND m.deleted = 0) " +
            "<if test='workshop != null and workshop != \"\"'> AND o.workshop = #{workshop} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (o.task_no LIKE '%' + #{keyword} + '%' OR o.product_name LIKE '%' + #{keyword} + '%' " +
            "       OR o.sub_material_code LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY o.create_time DESC, o.task_no" +
            "</script>")
    List<WageOrderVO> selectOrderList(@Param("workshop") String workshop,
                                      @Param("keyword") String keyword);

    /**
     * 按任务单号查询单据头
     */
    @Select("SELECT id, task_no, feed_no, product_name, sub_material_code AS materialCode, " +
            "product_qty, workshop, sync_time, deleted, create_time, update_time " +
            "FROM wage_order WHERE task_no = #{taskNo} AND deleted = 0")
    WageOrder selectByTaskNo(@Param("taskNo") String taskNo);
}
