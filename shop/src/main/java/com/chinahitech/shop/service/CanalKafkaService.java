package com.chinahitech.shop.service;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.chinahitech.shop.bean.Brand;
import com.chinahitech.shop.bean.Category;
import com.chinahitech.shop.mapper.BrandMapper;
import com.chinahitech.shop.mapper.CategoryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CanalKafkaService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Kafka Topic名称
    private static final String TOPIC_BRAND = "shop.db.brand";
    private static final String TOPIC_CATEGORY = "shop.db.category";

    /**
     * 同步品牌数据到Kafka
     */
    public void syncBrandToKafka(CanalEntry.EventType eventType, List<CanalEntry.Column> columns) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", eventType.name());
            message.put("table", "brand");
            message.put("timestamp", System.currentTimeMillis());

            // 提取数据
            Map<String, Object> data = new HashMap<>();
            for (CanalEntry.Column column : columns) {
                data.put(column.getName(), column.getValue());
            }
            message.put("data", data);

            // 发送到Kafka
            kafkaTemplate.send(TOPIC_BRAND, message);
            System.out.println("品牌数据已发送到Kafka: " + message);

        } catch (Exception e) {
            System.err.println("发送品牌数据到Kafka失败: " + e.getMessage());
        }
    }

    /**
     * 同步分类数据到Kafka
     */
    public void syncCategoryToKafka(CanalEntry.EventType eventType, List<CanalEntry.Column> columns) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", eventType.name());
            message.put("table", "category");
            message.put("timestamp", System.currentTimeMillis());

            Map<String, Object> data = new HashMap<>();
            for (CanalEntry.Column column : columns) {
                data.put(column.getName(), column.getValue());
            }
            message.put("data", data);

            kafkaTemplate.send(TOPIC_CATEGORY, message);
            System.out.println("分类数据已发送到Kafka: " + message);

        } catch (Exception e) {
            System.err.println("发送分类数据到Kafka失败: " + e.getMessage());
        }
    }
}
