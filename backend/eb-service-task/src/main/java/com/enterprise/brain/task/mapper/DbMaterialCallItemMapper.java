package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.DbMaterialCallItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 补退料申请明细表 Mapper
 */
@Mapper
public interface DbMaterialCallItemMapper extends BaseMapper<DbMaterialCallItem> {

    @Select("select * from DB_MATERIAL_CALL_ITEM where CALL_ID = #{callId} order by ORDER_CODE, MATERIAL_CODE")
    List<DbMaterialCallItem> selectByCallId(@Param("callId") String callId);
}
