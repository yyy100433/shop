package com.chinahitech.shop.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类（适配主从集群：写操作→主节点，读操作→从节点）
 * 增强：支持无过期时间写入、批量删除、Pattern删除（适配业务层缓存清理）
 */
@Component
public class RedisUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 基础写操作（主节点） ====================
    /**
     * 写入缓存（带过期时间，秒）
     * @param key 键
     * @param value 值
     * @param timeout 过期时间（秒）
     */
    public void set(String key, Object value, long timeout) {
        try {
            if (timeout <= 0) {
                // 过期时间≤0时，设置永久缓存（兼容业务层初始化缓存场景）
                redisTemplate.opsForValue().set(key, value);
            } else {
                redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis写入失败（key=" + key + "）：" + e.getMessage(), e);
        }
    }

    /**
     * 写入缓存（无过期时间，永久有效）
     * 适配场景：项目启动时初始化缓存（如CategoryService.initCategoryCache）
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        set(key, value, 0); // 复用带超时方法，超时时间传0表示永久
    }

    // ==================== 基础读操作（从节点） ====================
    /**
     * 查询缓存
     * @param key 键
     * @return 缓存值（null表示未命中/查询失败）
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            System.err.println("Redis查询失败（key=" + key + "）：" + e.getMessage());
            return null;
        }
    }

    // ==================== 基础删除操作（主节点） ====================
    /**
     * 删除单个缓存
     * @param key 键
     * @return 是否删除成功
     */
    public void delete(String key) {
        if (key != null && key.contains("*")) {
            // 如果是通配符，使用 scan 方式删除
            Set<String> keys = redisTemplate.keys(key);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } else {
            redisTemplate.delete(key);
        }
    }


    // ==================== 扩展方法（可选，增强实用性） ====================
    /**
     * 判断Key是否存在
     * @param key 键
     * @return true=存在，false=不存在/查询失败
     */
    public Boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            System.err.println("Redis判断Key是否存在失败（key=" + key + "）：" + e.getMessage());
            return false;
        }
    }

    /**
     * 设置缓存过期时间
     * @param key 键
     * @param timeout 过期时间（秒）
     * @return 是否设置成功
     */
    public Boolean expire(String key, long timeout) {
        try {
            return redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("Redis设置过期时间失败（key=" + key + "）：" + e.getMessage());
            return false;
        }
    }
}