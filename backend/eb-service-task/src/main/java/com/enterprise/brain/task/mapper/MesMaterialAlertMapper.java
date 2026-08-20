package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.MesMaterialAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 物料预警 Mapper
 */
@Mapper
public interface MesMaterialAlertMapper extends BaseMapper<MesMaterialAlert> {

    /**
     * 按状态查询预警；status 为 'all' 或空时查全部
     */
    @Select("<script>" +
            "select id,task_id,type,level,message,status,created_at " +
            "from mes_dwd_material_alert " +
            "<where> " +
            "  <if test='status != null and status != \"\" and status != \"all\"'>and status = #{status}</if> " +
            "</where> " +
            "order by created_at desc" +
            "</script>")
    List<MesMaterialAlert> selectByStatus(@Param("status") String status);

    /**
     * 确认送达时将对应缺料预警置为已解决（按任务号 + 物料名称模糊匹配 message）
     */
    @Update("update mes_dwd_material_alert set status = 'resolved' " +
            "where task_id = #{taskId} and type = 'shortage' and status = 'active' " +
            "and message like concat('%', #{materialName}, '%')")
    int resolveByTaskAndMaterial(@Param("taskId") String taskId, @Param("materialName") String materialName);
}
