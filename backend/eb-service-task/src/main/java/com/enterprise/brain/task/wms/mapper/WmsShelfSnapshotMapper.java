package com.enterprise.brain.task.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.wms.entity.WmsShelfSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface WmsShelfSnapshotMapper extends BaseMapper<WmsShelfSnapshot> {

    /** 查最新一代的 generation 值（空表时返回 0） */
    @Select("SELECT ISNULL(MAX(generation), 0) FROM wms_shelf_snapshot")
    int selectMaxGeneration();

    /** 删除指定代次的所有记录（用于清空当前代，准备重新插入） */
    @Delete("DELETE FROM wms_shelf_snapshot WHERE generation = #{gen}")
    int deleteByGeneration(@Param("gen") int generation);

    /** 查指定代次的所有快照（用于和上代对比） */
    @Select("SELECT * FROM wms_shelf_snapshot WHERE generation = #{gen}")
    List<WmsShelfSnapshot> selectByGeneration(@Param("gen") int generation);
}
