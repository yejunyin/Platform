package com.enterprise.brain.task;

import com.enterprise.brain.common.config.MybatisPlusConfig;
import com.enterprise.brain.common.config.MybatisPlusMetaHandler;
import com.enterprise.brain.common.util.DistributedLockUtil;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 单元测试配置 - 模拟Redisson分布式锁等外部依赖
 */
@TestConfiguration
@Import({MybatisPlusConfig.class, MybatisPlusMetaHandler.class})
public class TestConfig {

    @MockBean
    private DistributedLockUtil distributedLockUtil;

    @org.springframework.context.annotation.Bean
    public DistributedLockUtil distributedLockUtil() {
        DistributedLockUtil mock = org.mockito.Mockito.mock(DistributedLockUtil.class);

        // 模拟tryLock带返回值的方法 - 直接执行supplier
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(mock).tryLock(any(String.class), anyLong(), anyLong(), any(Supplier.class));

        // 模拟tryLock无返回值的方法
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(3);
            runnable.run();
            return null;
        }).when(mock).tryLock(any(String.class), anyLong(), anyLong(), any(Runnable.class));

        return mock;
    }
}
