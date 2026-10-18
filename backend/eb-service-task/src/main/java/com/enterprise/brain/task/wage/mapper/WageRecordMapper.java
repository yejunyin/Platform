package com.enterprise.brain.task.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.wage.entity.WageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WageRecordMapper extends BaseMapper<WageRecord> {

    /**
     * 按工资单号 WG{YYYYMM}-{工号} 定位
     */
    @Select("SELECT * FROM wage_record WHERE wage_no = #{wageNo} AND deleted = 0")
    WageRecord selectByWageNo(@Param("wageNo") String wageNo);
}
