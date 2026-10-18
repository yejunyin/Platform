package com.enterprise.brain.task.wage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.brain.common.exception.BusinessException;
import com.enterprise.brain.task.wage.entity.WagePrice;
import com.enterprise.brain.task.wage.mapper.WagePriceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 单价工序资料维护
 */
@Service
public class WagePriceService {

    @Resource
    private WagePriceMapper priceMapper;

    public List<WagePrice> list(String processName, String status, String keyword) {
        String kw = keyword == null ? null : keyword.trim();
        LambdaQueryWrapper<WagePrice> qw = new LambdaQueryWrapper<WagePrice>()
                .orderByDesc(WagePrice::getId);
        if (processName != null && !processName.isEmpty()) {
            qw.eq(WagePrice::getProcessName, processName);
        }
        if (status != null && !status.isEmpty()) {
            qw.eq(WagePrice::getStatus, status);
        }
        if (kw != null && !kw.isEmpty()) {
            qw.and(w -> w.like(WagePrice::getMaterialCode, kw)
                    .or().like(WagePrice::getMaterialName, kw));
        }
        return priceMapper.selectList(qw);
    }

    @Transactional(rollbackFor = Exception.class)
    public WagePrice save(WagePrice price) {
        if (price.getMaterialCode() == null || price.getMaterialCode().trim().isEmpty()) {
            throw new BusinessException("请填写子物料代码");
        }
        if (price.getProcessName() == null || price.getProcessName().trim().isEmpty()) {
            throw new BusinessException("请选择工序名称");
        }
        if (price.getUnitPrice() == null) {
            throw new BusinessException("请填写单价");
        }
        price.setMaterialCode(price.getMaterialCode().trim());
        price.setProcessName(price.getProcessName().trim());
        if (price.getStatus() == null || price.getStatus().isEmpty()) {
            price.setStatus("启用");
        }
        if (price.getId() == null) {
            priceMapper.insert(price);
        } else {
            WagePrice old = priceMapper.selectById(price.getId());
            if (old == null) {
                throw new BusinessException("单价记录不存在或已删除");
            }
            old.setMaterialCode(price.getMaterialCode());
            old.setMaterialName(price.getMaterialName());
            old.setProcessName(price.getProcessName());
            old.setUnitPrice(price.getUnitPrice());
            old.setEffectiveDate(price.getEffectiveDate());
            old.setExpiryDate(price.getExpiryDate());
            old.setStatus(price.getStatus());
            priceMapper.updateById(old);
            price = old;
        }
        return price;
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        if (priceMapper.selectById(id) == null) {
            throw new BusinessException("单价记录不存在或已删除");
        }
        priceMapper.deleteById(id);
    }

    /**
     * 按 子物料代码 + 工序 匹配当前有效单价（启用，生效/失效日期覆盖今天；多条取最新生效）
     * @return 单价；无匹配返回 null
     */
    public WagePrice matchPrice(String materialCode, String processName) {
        if (materialCode == null || materialCode.isEmpty() || processName == null || processName.isEmpty()) {
            return null;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        List<WagePrice> list = priceMapper.selectList(new LambdaQueryWrapper<WagePrice>()
                .eq(WagePrice::getMaterialCode, materialCode.trim())
                .eq(WagePrice::getProcessName, processName.trim())
                .eq(WagePrice::getStatus, "启用")
                .and(w -> w.isNull(WagePrice::getEffectiveDate).or().le(WagePrice::getEffectiveDate, today))
                .and(w -> w.isNull(WagePrice::getExpiryDate).or().ge(WagePrice::getExpiryDate, today))
                .orderByDesc(WagePrice::getEffectiveDate)
                .orderByDesc(WagePrice::getId));
        return list.isEmpty() ? null : list.get(0);
    }
}
