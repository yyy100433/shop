package com.chinahitech.shop.service;

import com.chinahitech.shop.bean.Brand;
import com.chinahitech.shop.bean.Category;
import com.chinahitech.shop.es.BrandDocument;
import com.chinahitech.shop.es.BrandDocumentRepository;
import com.chinahitech.shop.es.CategoryDocument;
import com.chinahitech.shop.es.CategoryDocumentRepository;
import com.chinahitech.shop.mapper.BrandMapper;
import com.chinahitech.shop.mapper.CategoryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaService {

    @Autowired
    private BrandDocumentRepository brandDocumentRepository;

    @Autowired
    private CategoryDocumentRepository categoryDocumentRepository;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "shop.db.brand", groupId = "shop-es-sync-group")
    public void consumeBrandMessage(String message) {
        try {
            Map<String, Object> messageMap = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            String eventType = (String) messageMap.get("eventType");
            Map<String, Object> data = (Map<String, Object>) messageMap.get("data");

            Long id = Long.parseLong(data.get("id").toString());

            switch (eventType) {
                case "INSERT":
                case "UPDATE":
                    // 查询数据库获取完整信息（包括分类名称）
                    Brand brand = brandMapper.selectById(id);
                    if (brand != null) {
                        Category category = categoryMapper.selectById(brand.getCategoryId());
                        String categoryName = category != null ? category.getName() : "";

                        BrandDocument document = new BrandDocument(brand, categoryName);
                        brandDocumentRepository.save(document);
                        System.out.println("品牌数据同步到ES成功: id=" + id);
                    }
                    break;

                case "DELETE":
                    brandDocumentRepository.deleteById(id);
                    System.out.println("品牌数据从ES删除成功: id=" + id);
                    break;

                default:
                    System.out.println("忽略品牌事件类型: " + eventType);
            }
        } catch (Exception e) {
            System.err.println("处理品牌Kafka消息失败: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "shop.db.category", groupId = "shop-es-sync-group")
    public void consumeCategoryMessage(String message) {
        try {
            Map<String, Object> messageMap = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            String eventType = (String) messageMap.get("eventType");
            Map<String, Object> data = (Map<String, Object>) messageMap.get("data");

            Long id = Long.parseLong(data.get("id").toString());

            switch (eventType) {
                case "INSERT":
                case "UPDATE":
                    Category category = categoryMapper.selectById(id);
                    if (category != null) {
                        CategoryDocument document = new CategoryDocument(category);
                        categoryDocumentRepository.save(document);
                        System.out.println("分类数据同步到ES成功: id=" + id);
                    }
                    break;

                case "DELETE":
                    categoryDocumentRepository.deleteById(id);
                    System.out.println("分类数据从ES删除成功: id=" + id);
                    break;

                default:
                    System.out.println("忽略分类事件类型: " + eventType);
            }
        } catch (Exception e) {
            System.err.println("处理分类Kafka消息失败: " + e.getMessage());
        }
    }
}