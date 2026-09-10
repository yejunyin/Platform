package com.enterprise.brain.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 补退料（生产补料/生产退料）配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "material")
public class MaterialProperties {

    private Wms wms = new Wms();
    private DingTalk dingTalk = new DingTalk();

    /** 补退料允许的生产订单状态（金蝶FStatus，逗号分隔；3下达 4开工） */
    private String allowMoStatus = "2,3,4";

    public Set<String> allowMoStatusSet() {
        return Arrays.stream(allowMoStatus.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * WMS 出库申请对接配置（M7）
     */
    @Data
    public static class Wms {
        /** 是否启用WMS对接；false时所有仓库视为非WMS仓库 */
        private boolean enabled = false;
        private String baseUrl = "";
        private String outboundPath = "/outbound/apply";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 15000;
        /** 启用WMS管理的仓库编码清单；为空且enabled=true时所有仓库都走WMS */
        private List<String> warehouseCodes = new ArrayList<>();

        /** 判断仓库是否启用WMS */
        public boolean isWmsWarehouse(String stockNumber) {
            if (!enabled) return false;
            if (warehouseCodes == null || warehouseCodes.isEmpty()) return true;
            return stockNumber != null && warehouseCodes.contains(stockNumber);
        }
    }

    /**
     * 钉钉消息推送配置（M8）
     */
    @Data
    public static class DingTalk {
        /** 是否启用；未配置webhook时降级为日志 */
        private boolean enabled = false;
        private String webhook = "";
    }
}
