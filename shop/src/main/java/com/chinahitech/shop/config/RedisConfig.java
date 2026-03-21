package com.chinahitech.shop.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.protocol.ProtocolVersion;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 哨兵模式配置类（适配 Canal 同步 Redis 缓存需求，已修复核心问题）
 */
@Slf4j
@Configuration
public class RedisConfig {

    // 哨兵核心配置
    @Value("${spring.redis.sentinel.master}")
    private String sentinelMasterName;
    @Value("${spring.redis.sentinel.nodes}")
    private String sentinelNodes;

    // 通用配置
    @Value("${spring.redis.database:0}")
    private int database;
    @Value("${spring.redis.password:}")
    private String password;
    @Value("${spring.redis.timeout:20000}")
    private long timeout;

    // 连接池配置
    @Value("${spring.redis.lettuce.pool.max-active:10}")
    private int maxActive;
    @Value("${spring.redis.lettuce.pool.max-idle:5}")
    private int maxIdle;
    @Value("${spring.redis.lettuce.pool.min-idle:2}")
    private int minIdle;
    @Value("${spring.redis.lettuce.pool.max-wait:10000}")
    private long maxWait;

    /**
     * 构建 Lettuce 连接工厂（修复核心问题：过滤无效节点 + 兼容协议）
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 1. 哨兵配置（核心修复：过滤无效节点）
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration();
        sentinelConfig.master(sentinelMasterName);

        Set<RedisNode> sentinelNodeSet = new HashSet<>();
        // 拆分哨兵节点并过滤无效地址
        Arrays.stream(sentinelNodes.split(","))
                .map(String::trim) // 去除空格
                .filter(node -> node.contains(":")) // 只保留 "IP:端口" 格式的有效节点
                .forEach(node -> {
                    try {
                        String[] parts = node.split(":");
                        String host = parts[0].trim();
                        int port = Integer.parseInt(parts[1].trim());
                        // 额外校验端口合法性
                        if (port > 0 && port < 65536) {
                            sentinelNodeSet.add(new RedisNode(host, port));
                            log.info("添加有效哨兵节点：{}:{}", host, port);
                        } else {
                            log.warn("跳过无效端口的哨兵节点：{}", node);
                        }
                    } catch (Exception e) {
                        log.warn("解析哨兵节点失败，跳过该节点：{}，错误：{}", node, e.getMessage());
                    }
                });

        // 校验有效节点数量
        if (sentinelNodeSet.isEmpty()) {
            log.error("未找到有效哨兵节点！配置的节点：{}", sentinelNodes);
            throw new RuntimeException("Redis 哨兵节点配置无效，无可用节点");
        }
        sentinelConfig.setSentinels(sentinelNodeSet);
        sentinelConfig.setDatabase(database);
        // 修复：密码配置用 RedisPassword 封装（更规范）
        if (!password.isEmpty()) {
            sentinelConfig.setPassword(RedisPassword.of(password));
        }

        // 2. 连接池配置（增强稳定性）
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWaitMillis(maxWait);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        // 3. 修复：兼容 RESP2 协议（避免版本不兼容）
        ClientOptions clientOptions = ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2) // 改为 RESP2（Redis 默认）
                .autoReconnect(true) // 自动重连
                .pingBeforeActivateConnection(true) // 连接前先 ping 检测
                .build();

        // 4. 构建 Lettuce 客户端配置
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .poolConfig(poolConfig)
                .readFrom(ReadFrom.REPLICA_PREFERRED) // 优先读从节点
                .clientOptions(clientOptions)
                .build();

        // 5. 创建连接工厂
        LettuceConnectionFactory factory = new LettuceConnectionFactory(sentinelConfig, clientConfig);
        factory.setValidateConnection(true);
        factory.afterPropertiesSet();
        log.info("Redis 哨兵连接工厂初始化完成，有效哨兵节点数：{}", sentinelNodeSet.size());
        return factory;
    }

    /**
     * 配置 RedisTemplate（序列化优化，适配 Canal 数据同步）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);

        // 1. Key/HashKey 序列化（字符串）
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);

        // 2. JSON 序列化配置（修复并优化）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule()); // 支持 JDK8 时间类型
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jsonSerializer.setObjectMapper(objectMapper);

        // 3. Value/HashValue 序列化（JSON）
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        // 4. 初始化配置
        redisTemplate.setEnableTransactionSupport(false);
        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    /**
     * StringRedisTemplate（Canal 同步简单字符串缓存专用）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory factory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(factory);
        stringRedisTemplate.afterPropertiesSet();
        return stringRedisTemplate;
    }
}