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

    /**
     * 按申请单查询明细。ID 为 MyBatis-Plus 雪花主键（同批次插入单调递增、等长数字串），
     * 按 ID 排序即 submit 入库顺序；v1.2 行拆分后同订单同物料多行（含 qty 也相同的行）依赖该顺序区分。
     */
    @Select("select * from DB_MATERIAL_CALL_ITEM where CALL_ID = #{callId} order by ID")
    List<DbMaterialCallItem> selectByCallId(@Param("callId") String callId);
}
