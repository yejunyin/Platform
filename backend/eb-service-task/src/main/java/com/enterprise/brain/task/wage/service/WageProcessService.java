package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wage.entity.WageProcess;
import com.enterprise.brain.task.wage.mapper.WageProcessMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工序基础资料维护
 */
@Service
public class WageProcessService {

    @Resource
    private WageProcessMapper processMapper;

    public List<WageProcess> list(String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        LambdaQueryWrapper<WageProcess> qw = new LambdaQueryWrapper<WageProcess>()
                .orderByAsc(WageProcess::getId);
        if (kw != null && !kw.isEmpty()) {
            qw.and(w -> w.like(WageProcess::getProcessCode, kw)
                    .or().like(WageProcess::getProcessName, kw)
                    .or().like(WageProcess::getWorkshop, kw));
        }
        return processMapper.selectList(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public WageProcess save(WageProcess process) {
        if (process.getProcessCode() == null || process.getProcessCode().trim().isEmpty()) {
            throw new BusinessException("请填写工序编号");
        }
        if (process.getProcessName() == null || process.getProcessName().trim().isEmpty()) {
            throw new BusinessException("请填写工序名称");
        }
        process.setProcessCode(process.getProcessCode().trim());
        process.setProcessName(process.getProcessName().trim());
        if (process.getId() == null) {
            Long dup = processMapper.selectCount(new LambdaQueryWrapper<WageProcess>()
                    .eq(WageProcess::getProcessCode, process.getProcessCode()));
            if (dup != null && dup > 0) {
                throw new BusinessException("工序编号已存在：" + process.getProcessCode());
            }
            processMapper.insert(process);
        } else {
            WageProcess old = processMapper.selectById(process.getId());
            if (old == null) {
                throw new BusinessException("工序不存在或已删除");
            }
            Long dup = processMapper.selectCount(new LambdaQueryWrapper<WageProcess>()
                    .eq(WageProcess::getProcessCode, process.getProcessCode())
                    .ne(WageProcess::getId, process.getId()));
            if (dup != null && dup > 0) {
                throw new BusinessException("工序编号已存在：" + process.getProcessCode());
            }
            old.setProcessCode(process.getProcessCode());
            old.setProcessName(process.getProcessName());
            old.setWorkshop(process.getWorkshop());
            old.setRemark(process.getRemark());
            processMapper.updateById(old);
            process = old;
        }
        return process;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (processMapper.selectById(id) == null) {
            throw new BusinessException("工序不存在或已删除");
        }
        processMapper.deleteById(id);
    }
}
