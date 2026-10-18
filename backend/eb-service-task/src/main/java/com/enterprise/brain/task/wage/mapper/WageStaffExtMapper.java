package com.enterprise.brain.task.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.wage.dto.WageStaffVO;
import com.enterprise.brain.task.wage.entity.WageStaffExt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WageStaffExtMapper extends BaseMapper<WageStaffExt> {

    /**
     * 员工资料列表：sys_user 左联工资扩展表与小组表；
     * status 取 sys_user.status（1 在职 / 0 停用离职）。
     */
    @Select("<script>" +
            "SELECT u.id AS userId, u.username, u.real_name AS realName, u.status AS status, " +
            "       e.id AS extId, e.job_type AS jobType, e.group_id AS groupId, " +
            "       g.group_name AS groupName, e.coef AS coef, e.hire_date AS hireDate " +
            "FROM sys_user u " +
            "LEFT JOIN wage_staff_ext e ON e.user_id = u.id AND e.deleted = 0 " +
            "LEFT JOIN wage_group g ON g.id = e.group_id AND g.deleted = 0 " +
            "WHERE u.deleted = 0 " +
            "<if test='groupId != null'> AND e.group_id = #{groupId} </if>" +
            "<if test='status != null'> AND u.status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (u.username LIKE '%' + #{keyword} + '%' OR u.real_name LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY u.id" +
            "</script>")
    List<WageStaffVO> selectStaffList(@Param("keyword") String keyword,
                                      @Param("groupId") Long groupId,
                                      @Param("status") Integer status);

    /**
     * 某组在职成员（工资分摊用）：sys_user.status=1 且扩展表有所属小组
     */
    @Select("SELECT u.id AS userId, u.username, u.real_name AS realName, u.status AS status, " +
            "       e.id AS extId, e.job_type AS jobType, e.group_id AS groupId, " +
            "       g.group_name AS groupName, e.coef AS coef, e.hire_date AS hireDate " +
            "FROM wage_staff_ext e " +
            "JOIN sys_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 1 " +
            "LEFT JOIN wage_group g ON g.id = e.group_id AND g.deleted = 0 " +
            "WHERE e.deleted = 0 AND e.group_id = #{groupId}")
    List<WageStaffVO> selectActiveMembers(@Param("groupId") Long groupId);

    /**
     * 全部分摊参与成员（在职且已分配小组）
     */
    @Select("SELECT u.id AS userId, u.username, u.real_name AS realName, u.status AS status, " +
            "       e.id AS extId, e.job_type AS jobType, e.group_id AS groupId, " +
            "       g.group_name AS groupName, e.coef AS coef, e.hire_date AS hireDate " +
            "FROM wage_staff_ext e " +
            "JOIN sys_user u ON u.id = e.user_id AND u.deleted = 0 AND u.status = 1 " +
            "LEFT JOIN wage_group g ON g.id = e.group_id AND g.deleted = 0 " +
            "WHERE e.deleted = 0 AND e.group_id IS NOT NULL")
    List<WageStaffVO> selectAllActiveMembers();
}
