package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.DbMaterialCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 补退料申请主表 Mapper
 */
@Mapper
public interface DbMaterialCallMapper extends BaseMapper<DbMaterialCall> {

    /**
     * 我的申请列表：按申请人过滤；status 为空查全部，多个状态逗号分隔；可选时间范围
     */
    @Select("<script>" +
            "select * from DB_MATERIAL_CALL " +
            "<where>" +
            "  <if test='applicantCode != null and applicantCode != \"\"'>and APPLICANT_CODE = #{applicantCode}</if>" +
            "  <if test='statuses != null and statuses.size() > 0'>" +
            "    and STATUS in <foreach collection='statuses' item='s' open='(' separator=',' close=')'>#{s}</foreach>" +
            "  </if>" +
            "  <if test='startTime != null'>and CREATE_TIME &gt;= #{startTime}</if>" +
            "  <if test='endTime != null'>and CREATE_TIME &lt;= #{endTime}</if>" +
            "</where>" +
            "order by CREATE_TIME desc" +
            "</script>")
    List<DbMaterialCall> selectMyApplications(@Param("applicantCode") String applicantCode,
                                              @Param("statuses") List<Integer> statuses,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /**
     * 质检待审列表：状态=10，按质检员ID/工号匹配；无匹配时由服务层回退全部待审
     */
    @Select("<script>" +
            "select * from DB_MATERIAL_CALL " +
            "where STATUS = 10 " +
            "<if test='qcStaff != null and qcStaff != \"\"'>" +
            "  and (QC_STAFF_ID = #{qcStaff} or QC_STAFF_CODE = #{qcStaff} or QC_STAFF_NAME = #{qcStaff})" +
            "</if>" +
            "order by CREATE_TIME asc" +
            "</script>")
    List<DbMaterialCall> selectAuditList(@Param("qcStaff") String qcStaff);
}
