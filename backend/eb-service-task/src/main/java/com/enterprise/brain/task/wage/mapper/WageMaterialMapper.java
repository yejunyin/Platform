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
     * 用料清单查询：taskNo 非空只查该单；否则按关键字/业务状态查全部
     */
    @Select("<script>" +
            "SELECT * FROM wage_material WHERE deleted = 0 " +
            "<if test='taskNo != null and taskNo != \"\"'> AND task_no = #{taskNo} </if>" +
            "<if test='bizStatus != null and bizStatus != \"\"'> AND biz_status = #{bizStatus} </if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "  AND (task_no LIKE '%' + #{keyword} + '%' OR feed_no LIKE '%' + #{keyword} + '%' " +
            "       OR material_code LIKE '%' + #{keyword} + '%' OR material_name LIKE '%' + #{keyword} + '%') " +
            "</if>" +
            "ORDER BY task_no, material_code" +
            "</script>")
    List<WageMaterial> selectMaterials(@Param("taskNo") String taskNo,
                                       @Param("bizStatus") String bizStatus,
                                       @Param("keyword") String keyword);

    /**
     * 同单号 + 物料代码是否已存在（开工下发去重）
     */
    @Select("SELECT COUNT(1) FROM wage_material WHERE deleted = 0 AND task_no = #{taskNo} AND material_code = #{materialCode}")
    int countByTaskAndMaterial(@Param("taskNo") String taskNo, @Param("materialCode") String materialCode);

    /**
     * 任务单状态联动：批量更新该单清单业务状态
     */
    @Update("UPDATE wage_material SET biz_status = #{bizStatus}, update_time = SYSDATETIME() " +
            "WHERE deleted = 0 AND task_no = #{taskNo}")
    int updateBizStatusByTaskNo(@Param("taskNo") String taskNo, @Param("bizStatus") String bizStatus);
}
