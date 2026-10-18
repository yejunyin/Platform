package com.enterprise.brain.task.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.wage.entity.WagePayStat;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WagePayStatMapper extends BaseMapper<WagePayStat> {

    /**
     * 重新计算前物理清空（明细为系统生成数据，不保留逻辑删除垃圾）
     */
    @Delete("DELETE FROM wage_pay_stat")
    int deleteAllPhysical();

    /**
     * 已出现过的工资月份（倒序，供下拉）
     */
    @Select("SELECT DISTINCT wage_month FROM wage_pay_stat WHERE deleted = 0 ORDER BY wage_month DESC")
    List<String> selectDistinctMonths();

    /**
     * 列表查询（粗筛，列级排序/筛选在前端完成，与原单文件版口径一致）
     */
    @Select("<script>" +
            "SELECT * FROM wage_pay_stat WHERE deleted = 0 " +
            "<if test='month != null and month != \"\"'> AND wage_month = #{month} </if>" +
            "<if test='groupId != null'> AND group_id = #{groupId} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (staff_name LIKE '%' + #{keyword} + '%' OR staff_no LIKE '%' + #{keyword} + '%' " +
            "       OR task_no LIKE '%' + #{keyword} + '%' OR product_name LIKE '%' + #{keyword} + '%' " +
            "       OR sub_material_name LIKE '%' + #{keyword} + '%' OR job_type LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY wage_month DESC, group_name, staff_name" +
            "</script>")
    List<WagePayStat> selectStatList(@Param("month") String month,
                                     @Param("groupId") Long groupId,
                                     @Param("keyword") String keyword);
}
