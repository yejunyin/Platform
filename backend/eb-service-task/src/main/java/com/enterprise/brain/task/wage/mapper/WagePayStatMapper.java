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
     * 重新计算前物理清空（仅清除未复核行，已复核的保留，避免覆盖复核人/复核时间）
     * @deprecated 全局删除存在重复风险：多工单分批开工时，已复核行被保留但同工单新行重复插入，
     *             改用 {@link #deleteUnreviewedByTaskNo(String)} 按工单精确处理
     */
    @Deprecated
    @Delete("DELETE FROM wage_pay_stat WHERE reviewer IS NULL")
    int deleteUnreviewed();

    /**
     * 全表物理清空（仅在彻底重置工资体系时使用，慎用）
     */
    @Delete("DELETE FROM wage_pay_stat")
    int deleteAllPhysical();

    /**
     * 按生产任务单号精确清除未复核行
     * <p>recalc 按工单逐个重建，确保：已复核行保留 + 同工单未复核行清除 + 重建当前全量</p>
     */
    @Delete("DELETE FROM wage_pay_stat WHERE task_no = #{taskNo} AND reviewer IS NULL")
    int deleteUnreviewedByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 全表物理清空（指定工单，仅彻底重置某工单工资数据时使用）
     */
    @Delete("DELETE FROM wage_pay_stat WHERE task_no = #{taskNo}")
    int deleteAllByTaskNo(@Param("taskNo") String taskNo);

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
            "<if test='reviewStatus != null and reviewStatus == \"reviewed\"'> AND reviewer IS NOT NULL </if>" +
            "<if test='reviewStatus != null and reviewStatus == \"unreviewed\"'> AND reviewer IS NULL </if>" +
            "ORDER BY wage_month DESC, group_name, staff_name" +
            "</script>")
    List<WagePayStat> selectStatList(@Param("month") String month,
                                     @Param("groupId") Long groupId,
                                     @Param("keyword") String keyword,
                                     @Param("reviewStatus") String reviewStatus);
}
