package com.chinahitech.shop.config;

import com.chinahitech.shop.bean.Brand;
import com.chinahitech.shop.bean.Category;
import com.chinahitech.shop.es.BrandDocument;
import com.chinahitech.shop.es.CategoryDocument;
import com.chinahitech.shop.mapper.BrandMapper;
import com.chinahitech.shop.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class EsConfig implements CommandLineRunner {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public void run(String... args) throws Exception {
        // 创建索引（如果不存在）
        createIndices();

        // 全量同步数据（可选，首次运行时执行）
        // fullSyncData();
    }

    private void createIndices() {
        // 创建品牌索引
        IndexOperations brandIndexOps = elasticsearchRestTemplate.indexOps(BrandDocument.class);
        if (!brandIndexOps.exists()) {
            brandIndexOps.create();
            brandIndexOps.putMapping(brandIndexOps.createMapping());
            System.out.println("品牌索引创建成功");
        }

        // 创建分类索引
        IndexOperations categoryIndexOps = elasticsearchRestTemplate.indexOps(CategoryDocument.class);
        if (!categoryIndexOps.exists()) {
            categoryIndexOps.create();
            categoryIndexOps.putMapping(categoryIndexOps.createMapping());
            System.out.println("分类索引创建成功");
        }
    }

    private void fullSyncData() {
        // 全量同步品牌
        List<Brand> brands = brandMapper.findAllBrands();
        List<BrandDocument> brandDocs = brands.stream()
                .map(brand -> {
                    Category category = categoryMapper.selectById(brand.getCategoryId());
                    String categoryName = category != null ? category.getName() : "";
                    return new BrandDocument(brand, categoryName);
                })
                .collect(Collectors.toList());
        elasticsearchRestTemplate.save(brandDocs);
        System.out.println("全量同步品牌到ES完成，数量：" + brandDocs.size());

        // 全量同步分类
        List<Category> categories = categoryMapper.findAll();
        List<CategoryDocument> categoryDocs = categories.stream()
                .map(CategoryDocument::new)
                .collect(Collectors.toList());
        elasticsearchRestTemplate.save(categoryDocs);
        System.out.println("全量同步分类到ES完成，数量：" + categoryDocs.size());
    }
}