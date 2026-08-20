package com.enterprise.brain.common.util;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁工具类（基于Redisson）
 * <p>本地无 Redis 时 RedissonClient bean 不存在，此类不注册；
 * 此时 DistributedLockUtil 注入点需用 @Autowired(required=false) 或 Optional 兜底。</p>
 */
@Component
@ConditionalOnBean(RedissonClient.class)
public class DistributedLockUtil {

    @Resource
    private RedissonClient redissonClient;

    /**
     * 默认锁等待时间
     */
    private static final long DEFAULT_WAIT_TIME = 3;
    /**
     * 默认锁持有时间
     */
    private static final long DEFAULT_LEASE_TIME = 30;
    /**
     * 时间单位
     */
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * 尝试加锁执行
     *
     * @param lockKey  锁Key
     * @param supplier 执行业务
     * @param <T>      返回类型
     * @return 业务返回结果
     */
    public <T> T tryLock(String lockKey, Supplier<T> supplier) {
        return tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, supplier);
    }

    /**
     * 尝试加锁执行
     */
    public <T> T tryLock(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, DEFAULT_TIME_UNIT);
            if (!locked) {
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 尝试加锁执行（无返回值）
     */
    public void tryLock(String lockKey, Runnable runnable) {
        tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, runnable);
    }

    /**
     * 尝试加锁执行（无返回值）
     */
    public void tryLock(String lockKey, long waitTime, long leaseTime, Runnable runnable) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, DEFAULT_TIME_UNIT);
            if (!locked) {
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
            runnable.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
