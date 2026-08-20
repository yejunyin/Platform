package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.MesProductOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MES 生产订单 Mapper（只读，外加 BOMflag 维护）
 */
@Mapper
public interface MesProductOrderMapper extends BaseMapper<MesProductOrder> {

    /**
     * 拉取全部生产订单（按用户提供的原始 SQL；schedulepriority/unit 在 MES 中若不存在则以 NULL 兜底，Java 层再映射/赋默认值）
     */
    @Select("select id,producttype,starttime,ordercode,pcode,machcode,materialid,materialname,total,spec,dept,staffname," +
            "scheduledpriority as schedulepriority,unit,BOMflag " +
            "from mes_dwd_productOrder")
    List<MesProductOrder> selectAllOrders();

    /**
     * 按任务单号 pcode 查询单条订单（含 BOMflag）
     */
    @Select("select TOP 1 id,producttype,starttime,ordercode,pcode,machcode,materialid,materialname,total,spec,dept,staffname," +
            "scheduledpriority as schedulepriority,unit,BOMflag " +
            "from mes_dwd_productOrder " +
            "where pcode = #{pcode} order by id asc")
    MesProductOrder selectByPcode(@Param("pcode") String pcode);

    /**
     * 将指定任务的 BOMflag 置为 1（已同步）
     */
    @Update("update mes_dwd_productOrder set BOMflag = '1' where pcode = #{pcode}")
    int markBomSynced(@Param("pcode") String pcode);
}
