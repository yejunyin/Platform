package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.MesMaterialDetail;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备料明细 Mapper
 */
@Mapper
public interface MesMaterialDetailMapper extends BaseMapper<MesMaterialDetail> {

    /**
     * 按任务单号查询备料明细；taskId 为空则查全部
     */
    @Select("<script>" +
            "select id,task_id,material_code,material_name,specification,unit,required_qty,available_qty,prepared_qty,storage_location,status,preparer,prepare_time,remark " +
            "from mes_dwd_material_detail " +
            "<where> " +
            "  <if test='taskId != null and taskId != \"\"'>and task_id = #{taskId}</if> " +
            "</where> " +
            "order by id asc" +
            "</script>")
    List<MesMaterialDetail> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 按任务单号 + 物料编码查询单条备料明细
     */
    @Select("select TOP 1 id,task_id,material_code,material_name,specification,unit,required_qty,available_qty,prepared_qty,storage_location,status,preparer,prepare_time,remark " +
            "from mes_dwd_material_detail where task_id = #{taskId} and material_code = #{materialCode} order by id asc")
    MesMaterialDetail selectByTaskIdAndMaterialCode(@Param("taskId") String taskId, @Param("materialCode") String materialCode);

    /**
     * 删除指定任务的全部备料明细（用于从 ERP 重新同步前清理旧数据）
     */
    @Delete("delete from mes_dwd_material_detail where task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);

    /**
     * 标记备料中（备料按钮）：更新状态、备料时间；备料人仅在原值为空时回填（与前端 if(!preparer) 逻辑一致）
     */
    @Update("update mes_dwd_material_detail set status = #{status}, preparer = COALESCE(NULLIF(preparer, ''), #{preparer}), prepare_time = #{prepareTime} where id = #{id}")
    int updateStatus(@Param("id") Integer id, @Param("status") String status, @Param("preparer") String preparer, @Param("prepareTime") LocalDateTime prepareTime);

    /**
     * 备齐：累加已备数量，并按是否满足需求设置状态/备注
     */
    @Update("update mes_dwd_material_detail set prepared_qty = #{preparedQty}, status = #{status}, preparer = #{preparer}, prepare_time = #{prepareTime}, remark = #{remark} where id = #{id}")
    int updatePrepare(@Param("id") Integer id, @Param("preparedQty") Integer preparedQty, @Param("status") String status, @Param("preparer") String preparer, @Param("prepareTime") LocalDateTime prepareTime, @Param("remark") String remark);

    /**
     * 确认送达时累加已备数量并更新状态/备注（供 deliverCall 使用）
     */
    @Update("update mes_dwd_material_detail set prepared_qty = #{preparedQty}, status = #{status}, prepare_time = #{prepareTime}, remark = #{remark} where id = #{id}")
    int updateAfterDeliver(@Param("id") Integer id, @Param("preparedQty") Integer preparedQty, @Param("status") String status, @Param("prepareTime") LocalDateTime prepareTime, @Param("remark") String remark);

    /**
     * 叫料时仅更新备料状态与备注（不动 prepare_time / preparer，与前端 addCall 行为一致）
     */
    @Update("update mes_dwd_material_detail set status = #{status}, remark = #{remark} where id = #{id}")
    int updateStatusAndRemark(@Param("id") Integer id, @Param("status") String status, @Param("remark") String remark);
}
