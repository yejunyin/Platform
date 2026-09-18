package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统用户 Mapper（质检人员维护）
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 在职质检人员列表（deleted=0），支持按工号/姓名模糊检索
     */
    @Select("<script>" +
            "SELECT id, username, real_name, dingdingid AS dingdingId FROM sys_user WHERE deleted = 0 " +
            "<if test=\"keyword != null and keyword != ''\">" +
            "  AND (username LIKE '%' + #{keyword} + '%' OR real_name LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY id" +
            "</script>")
    List<SysUser> selectStaffList(@Param("keyword") String keyword);

    /**
     * 按工号查询（含已软删除记录，用于唯一校验/删除后恢复）
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 当前最大ID（表主键非自增，新增时应用层赋值）
     */
    @Select("SELECT ISNULL(MAX(id), 0) FROM sys_user")
    Long selectMaxId();

    /**
     * 软删除
     */
    @Update("UPDATE sys_user SET deleted = 1, update_time = SYSDATETIME() " +
            "WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") Long id);

    /**
     * 恢复已软删除的同工号记录并更新姓名、钉钉ID
     */
    @Update("UPDATE sys_user SET deleted = 0, real_name = #{realName}, dingdingid = #{dingdingId}, " +
            "update_time = SYSDATETIME() WHERE id = #{id}")
    int restoreById(@Param("id") Long id, @Param("realName") String realName,
                    @Param("dingdingId") String dingdingId);

    /**
     * 维护钉钉ID（dingdingId 传 null/空串可清空绑定）
     */
    @Update("UPDATE sys_user SET dingdingid = #{dingdingId}, update_time = SYSDATETIME() " +
            "WHERE id = #{id} AND deleted = 0")
    int updateDingdingId(@Param("id") Long id, @Param("dingdingId") String dingdingId);
}
