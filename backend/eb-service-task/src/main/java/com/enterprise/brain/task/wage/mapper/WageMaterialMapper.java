package com.enterprise.brain.task.wage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.brain.task.wage.entity.WageMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface WageMaterialMapper extends BaseMapper<WageMaterial> {

    /**
     * 用料清单查询（带加工组名称，MyBatis 自动下划线→驼峰映射）
     */
    @Select("<script>" +
            "SELECT m.*, g.group_name AS groupName " +
            "FROM wage_material m " +
            "LEFT JOIN wage_group g ON g.id = m.group_id AND g.deleted = 0 " +
            "WHERE m.deleted = 0 " +
            "<if test='taskNo != null and taskNo != \"\"'> AND m.task_no = #{taskNo} </if>" +
            "<if test='bizStatus != null and bizStatus != \"\"'> AND m.biz_status = #{bizStatus} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (m.task_no LIKE '%' + #{keyword} + '%' OR m.feed_no LIKE '%' + #{keyword} + '%' " +
            "       OR m.material_code LIKE '%' + #{keyword} + '%' OR m.material_name LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY m.task_no, m.material_code" +
            "</script>")
    List<WageMaterial> selectMaterialsWithGroup(@Param("taskNo") String taskNo,
                                                 @Param("bizStatus") String bizStatus,
                                                 @Param("keyword") String keyword);

    /**
     * 同单号 + 物料代码是否已存在
     */
    @Select("SELECT COUNT(1) FROM wage_material WHERE deleted = 0 AND task_no = #{taskNo} AND material_code = #{materialCode}")
    int countByTaskAndMaterial(@Param("taskNo") String taskNo, @Param("materialCode") String materialCode);

    /**
     * 按 task_no 批量更新状态（兼容历史/兜底用）
     */
    @Update("UPDATE wage_material SET biz_status = #{bizStatus}, update_time = SYSDATETIME() " +
            "WHERE deleted = 0 AND task_no = #{taskNo}")
    int updateBizStatusByTaskNo(@Param("taskNo") String taskNo, @Param("bizStatus") String bizStatus);
}
