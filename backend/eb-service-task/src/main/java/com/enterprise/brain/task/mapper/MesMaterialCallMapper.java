package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.MesMaterialCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 叫料记录 Mapper
 */
@Mapper
public interface MesMaterialCallMapper extends BaseMapper<MesMaterialCall> {

    /**
     * 按状态查询叫料记录；status 为 'all' 或空时查全部
     */
    @Select("<script>" +
            "select id,task_id,material_code,material_name,required_qty,call_qty,call_type,caller,call_time,status,responder,response_time,deliver_time,remark " +
            "from mes_dwd_material_call " +
            "<where> " +
            "  <if test='status != null and status != \"\" and status != \"all\"'>and status = #{status}</if> " +
            "</where> " +
            "order by call_time desc" +
            "</script>")
    List<MesMaterialCall> selectByStatus(@Param("status") String status);

    /**
     * 响应叫料：状态置 delivering，记录响应人/响应时间
     */
    @Update("update mes_dwd_material_call set status = #{status}, responder = #{responder}, response_time = #{responseTime} where id = #{id}")
    int updateRespond(@Param("id") Integer id, @Param("status") String status, @Param("responder") String responder, @Param("responseTime") LocalDateTime responseTime);

    /**
     * 确认送达：状态置 delivered，记录送达时间
     */
    @Update("update mes_dwd_material_call set status = #{status}, deliver_time = #{deliverTime} where id = #{id}")
    int updateDeliver(@Param("id") Integer id, @Param("status") String status, @Param("deliverTime") LocalDateTime deliverTime);
}
