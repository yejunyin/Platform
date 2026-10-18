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
     * 任务单分页列表（左联小组带出名称）；筛选在 SQL 内完成，分页由 service 内存切片
     */
    @Select("<script>" +
            "SELECT o.*, g.group_name AS groupName " +
            "FROM wage_order o " +
            "LEFT JOIN wage_group g ON g.id = o.group_id AND g.deleted = 0 " +
            "WHERE o.deleted = 0 " +
            "<if test='workshop != null and workshop != \"\"'> AND o.workshop = #{workshop} </if>" +
            "<if test='groupId != null'> AND o.group_id = #{groupId} </if>" +
            "<if test='settleStatus != null and settleStatus != \"\"'> AND o.settle_status = #{settleStatus} </if>" +
            "<if test='bizStatus != null and bizStatus != \"\"'> AND o.biz_status = #{bizStatus} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (o.task_no LIKE '%' + #{keyword} + '%' OR o.product_name LIKE '%' + #{keyword} + '%' " +
            "       OR o.sub_material_code LIKE '%' + #{keyword} + '%' OR o.sub_material_name LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY o.create_time DESC, o.task_no" +
            "</script>")
    List<WageOrderVO> selectOrderList(@Param("workshop") String workshop,
                                      @Param("groupId") Long groupId,
                                      @Param("settleStatus") String settleStatus,
                                      @Param("bizStatus") String bizStatus,
                                      @Param("keyword") String keyword);

    /**
     * 按任务单号查询（含已删除不查）
     */
    @Select("SELECT * FROM wage_order WHERE task_no = #{taskNo} AND deleted = 0")
    WageOrder selectByTaskNo(@Param("taskNo") String taskNo);
}
