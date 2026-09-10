package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.dto.response.ReservedQtyDTO;
import com.enterprise.brain.task.entity.DbMaterialCallBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 补退料批次匹配表 Mapper
 */
@Mapper
public interface DbMaterialCallBatchMapper extends BaseMapper<DbMaterialCallBatch> {

    @Select("select * from DB_MATERIAL_CALL_BATCH where CALL_ID = #{callId} order by MATERIAL_CODE, CREATE_TIME")
    List<DbMaterialCallBatch> selectByCallId(@Param("callId") String callId);

    /**
     * 汇总进行中申请(状态20/30/31/40/41/50)已预占的数量（按物料+批次），用于FIFO可用量扣减
     */
    @Select("select b.MATERIAL_CODE as materialCode, b.BATCH_NO as batchNo, sum(b.QTY) as qty " +
            "from DB_MATERIAL_CALL_BATCH b " +
            "join DB_MATERIAL_CALL c on c.ID = b.CALL_ID " +
            "where c.STATUS in (20,30,31,40,41,50) and b.RESERVED = 1 " +
            "group by b.MATERIAL_CODE, b.BATCH_NO")
    List<ReservedQtyDTO> sumActiveReservations();
}
