package com.enterprise.brain.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.dto.response.QcStaffDTO;
import com.enterprise.brain.task.entity.DbMaterialReason;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 补退料原因字典 Mapper
 */
@Mapper
public interface DbMaterialReasonMapper extends BaseMapper<DbMaterialReason> {

    @Select("select * from DB_MATERIAL_REASON where REASON_TYPE = #{reasonType} and ENABLED = 1 order by SORT_NO")
    List<DbMaterialReason> selectByType(@Param("reasonType") String reasonType);

    /** 按ID+类型查启用字典项（退料类型/退料原因合法性校验用） */
    @Select("select * from DB_MATERIAL_REASON where REASON_ID = #{id} and REASON_TYPE = #{reasonType} and ENABLED = 1")
    DbMaterialReason selectByIdAndType(@Param("id") String id, @Param("reasonType") String reasonType);

    /**
     * 全部在职员工（质检员列表）
     */
    @Select("select u.username as staffCode, u.real_name as staffName " +
            "from sys_user u " +
            "where u.deleted = 0 and u.status = 1 " +
            "order by u.id")
    List<QcStaffDTO> selectAllStaffList();
}
