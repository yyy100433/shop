package com.chinahitech.shop.config;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.chinahitech.shop.service.CanalKafkaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Canal 客户端配置类（适配 shop 数据库 + Redis 哨兵模式，保证双写一致性）
 * 核心功能：监听 MySQL binlog，同步数据到 Redis
 */
@Configuration // 仅保留此注解，删除@Component
public class CanalClientConfig {
    private static final Logger log = LoggerFactory.getLogger(CanalClientConfig.class);

    @Value("${canal.server.host:127.0.0.1}")
    private String canalServerHost;

    @Value("${canal.server.port:11111}")
    private int canalServerPort;

    @Value("${canal.destination:example}")
    private String canalDestination;

    @Value("${canal.username:}")
    private String canalUsername;

    @Value("${canal.password:}")
    private String canalPassword;

    @Value("${canal.listen.db:shop}")
    private String listenDb;

    @Value("${canal.listen.tables:brand,category,user}")
    private String listenTables;

    @Value("${redis.cache.prefix:shop:}")
    private String redisKeyPrefix;

    @Value("${redis.cache.expire.hours:24}")
    private long redisExpireHours;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 在 CanalClientConfig 类中注入 CanalKafkaService
    @Autowired(required = false)  // required=false 防止Kafka未配置时报错
    private CanalKafkaService canalKafkaService;

    private CanalConnector canalConnector;
    private ExecutorService executorService;

    private void initCanalConnector() {
        if (canalConnector == null) {
            canalConnector = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(canalServerHost, canalServerPort),
                    canalDestination,
                    canalUsername,
                    canalPassword
            );
            log.info("Canal 连接器初始化成功，服务地址：{}:{}", canalServerHost, canalServerPort);
        }
    }

    @PostConstruct
    public void startCanalListener() {
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("canal-binlog-listener");
            thread.setDaemon(true);
            return thread;
        });

        executorService.execute(() -> {
            try {
                initCanalConnector();
                canalConnector.connect();
                canalConnector.rollback();
                String subscribeFilter = buildSubscribeFilter();
                canalConnector.subscribe(subscribeFilter);
                log.info("Canal 订阅成功，过滤规则：{}", subscribeFilter);

                while (!Thread.currentThread().isInterrupted()) {
                    Message message = canalConnector.getWithoutAck(1000);
                    long batchId = message.getId();
                    int entrySize = message.getEntries().size();

                    if (batchId == -1 || entrySize == 0) {
                        TimeUnit.MILLISECONDS.sleep(1000);
                        continue;
                    }

                    handleBinlogEvent(message.getEntries());
                    canalConnector.ack(batchId);
                    log.debug("Canal 消费成功，批次ID：{}，处理条目数：{}", batchId, entrySize);
                }
            } catch (InterruptedException e) {
                log.warn("Canal 监听线程被中断，原因：{}", e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Canal 监听异常，开始重试...", e);
                canalConnector.rollback();
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                if (canalConnector != null) {
                    canalConnector.disconnect();
                    log.info("Canal 连接器已断开");
                }
            }
        });
        log.info("Canal 监听服务启动成功");
    }

    private String buildSubscribeFilter() {
        StringBuilder filter = new StringBuilder();
        String[] tables = listenTables.split(",");
        for (int i = 0; i < tables.length; i++) {
            filter.append(listenDb).append("\\.").append(tables[i].trim());
            if (i < tables.length - 1) {
                filter.append(",");
            }
        }
        return filter.toString();
    }

    private void handleBinlogEvent(List<CanalEntry.Entry> entries) {
        for (CanalEntry.Entry entry : entries) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
                continue;
            }

            try {
                CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
                String tableName = entry.getHeader().getTableName();
                CanalEntry.EventType eventType = rowChange.getEventType();

                log.debug("收到 binlog 事件：数据库={}，表={}，操作类型={}",
                        entry.getHeader().getSchemaName(), tableName, eventType);

                for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                    // 原有Redis同步逻辑
                    switch (eventType) {
                        case INSERT:
                        case UPDATE:
                            syncDataToRedis(tableName, rowData.getAfterColumnsList());
                            break;
                        case DELETE:
                            deleteRedisCache(tableName, rowData.getBeforeColumnsList());
                            break;
                        default:
                            log.debug("忽略不支持的操作类型：{}", eventType);
                            break;
                    }

                    // ====转发到Kafka（用于ES同步）====
                    if (canalKafkaService != null) {
                        switch (tableName) {
                            case "brand":
                                canalKafkaService.syncBrandToKafka(eventType,
                                        eventType == CanalEntry.EventType.DELETE ?
                                                rowData.getBeforeColumnsList() : rowData.getAfterColumnsList());
                                break;
                            case "category":
                                canalKafkaService.syncCategoryToKafka(eventType,
                                        eventType == CanalEntry.EventType.DELETE ?
                                                rowData.getBeforeColumnsList() : rowData.getAfterColumnsList());
                                break;
                            default:
                                // 其他表暂不同步到ES
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("处理 binlog 事件失败，表名：{}", entry.getHeader().getTableName(), e);
            }
        }
    }

    private void syncDataToRedis(String tableName, List<CanalEntry.Column> columns) {
        try {
            String id = getColumnValue(columns, "id");
            if (id == null || id.isEmpty()) {
                log.warn("表 {} 未找到主键 id，跳过同步", tableName);
                return;
            }

            String redisKey = redisKeyPrefix + tableName + ":" + id;
            Map<String, Object> dataMap = buildDataMap(columns);
            redisTemplate.opsForValue().set(redisKey, dataMap, redisExpireHours, TimeUnit.HOURS);
            log.info("Redis 同步成功：Key={}，数据={}", redisKey, dataMap);
        } catch (Exception e) {
            log.error("同步数据到 Redis 失败，表名：{}", tableName, e);
            try {
                TimeUnit.MILLISECONDS.sleep(500);
                syncDataToRedis(tableName, columns);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void deleteRedisCache(String tableName, List<CanalEntry.Column> columns) {
        try {
            String id = getColumnValue(columns, "id");
            if (id == null || id.isEmpty()) {
                log.warn("表 {} 未找到主键 id，跳过删除", tableName);
                return;
            }

            String redisKey = redisKeyPrefix + tableName + ":" + id;
            Boolean deleteResult = redisTemplate.delete(redisKey);
            if (Boolean.TRUE.equals(deleteResult)) {
                log.info("Redis 缓存删除成功：Key={}", redisKey);
            } else {
                log.debug("Redis 缓存不存在，无需删除：Key={}", redisKey);
            }
        } catch (Exception e) {
            log.error("删除 Redis 缓存失败，表名：{}", tableName, e);
        }
    }

    private Map<String, Object> buildDataMap(List<CanalEntry.Column> columns) {
        Map<String, Object> dataMap = new HashMap<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (CanalEntry.Column column : columns) {
            String colName = column.getName();
            String colValue = column.getValue();
            String mysqlType = column.getMysqlType().toLowerCase();

            try {
                if (mysqlType.contains("int") || mysqlType.contains("bigint") || mysqlType.contains("tinyint")) {
                    dataMap.put(colName, Long.parseLong(colValue)); // 替换Integer为Long，避免大整数溢出
                } else if (mysqlType.contains("decimal") || mysqlType.contains("float") || mysqlType.contains("double")) {
                    dataMap.put(colName, Double.parseDouble(colValue));
                } else if (mysqlType.contains("datetime") || mysqlType.contains("timestamp")) {
                    if (colValue != null && !colValue.isEmpty()) {
                        dataMap.put(colName, LocalDateTime.parse(colValue, dtf));
                    } else {
                        dataMap.put(colName, null);
                    }
                } else if (mysqlType.contains("bool") || mysqlType.contains("boolean")) {
                    dataMap.put(colName, Boolean.parseBoolean(colValue));
                } else {
                    dataMap.put(colName, colValue);
                }
            } catch (Exception e) {
                dataMap.put(colName, colValue);
                log.warn("字段 {} 类型转换失败，已存储原始值：{}", colName, colValue);
            }
        }
        return dataMap;
    }

    private String getColumnValue(List<CanalEntry.Column> columns, String columnName) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        for (CanalEntry.Column column : columns) {
            if (columnName.equals(column.getName())) {
                return column.getValue();
            }
        }
        return null;
    }

    @PreDestroy
    public void stopCanalListener() {
        log.info("开始关闭 Canal 监听服务...");
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (canalConnector != null) {
            canalConnector.disconnect();
        }
        log.info("Canal 监听服务已关闭");
    }
}