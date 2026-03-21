package com.chinahitech.shop.service;

import com.chinahitech.shop.bean.Brand;
import com.chinahitech.shop.bean.Category;
import com.chinahitech.shop.bean.CategoryTreeDTO;
import com.chinahitech.shop.mapper.BrandMapper;
import com.chinahitech.shop.mapper.CategoryMapper;
import com.chinahitech.shop.utils.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Resource
    private RedisUtil redisUtil;

    // 引入Jackson工具类进行安全的类型转换
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存Key常量
    private static final String CATEGORY_ALL_KEY = "category:all";
    private static final String CATEGORY_PARENT_KEY_PREFIX = "category:parent:";
    private static final long CACHE_EXPIRE_SECONDS = 30 * 60;

    public List<Category> getByParentId(Long parentId) {
        String cacheKey = CATEGORY_PARENT_KEY_PREFIX + parentId;
        List<Category> cacheCategories = null;

        try {
            Object cacheObj = redisUtil.get(cacheKey);
            if (cacheObj != null) {
                // 使用Jackson将缓存对象转换为指定类型的List<Category>
                cacheCategories = objectMapper.convertValue(
                        cacheObj,
                        new TypeReference<List<Category>>() {}
                );
                return cacheCategories;
            }
        } catch (Exception e) {
            System.err.println("Redis查询父ID分类缓存失败（parentId=" + parentId + "）：" + e.getMessage());
            // 缓存转换失败时，清空错误缓存，避免下次继续报错
            redisUtil.delete(cacheKey);
        }

        List<Category> dbCategories = categoryMapper.getByParentId(parentId);

        try {
            if (dbCategories != null && !dbCategories.isEmpty()) {
                redisUtil.set(cacheKey, dbCategories, CACHE_EXPIRE_SECONDS);
            }
        } catch (Exception e) {
            System.err.println("Redis存入父ID分类缓存失败（parentId=" + parentId + "）：" + e.getMessage());
        }

        return dbCategories;
    }

    /**
     * 项目启动时初始化全量分类缓存
     */
    @PostConstruct
    public void initCategoryCache() {
        try {
            List<Category> categoryList = categoryMapper.findAll();
            if (categoryList != null && !categoryList.isEmpty()) {
                redisUtil.set(CATEGORY_ALL_KEY, categoryList, CACHE_EXPIRE_SECONDS);
                System.out.println("初始化全量分类缓存成功，缓存Key：" + CATEGORY_ALL_KEY);
            }
        } catch (Exception e) {
            System.err.println("初始化全量分类缓存失败：" + e.getMessage());
        }
    }

    public List<Category> findAll() {
        List<Category> cacheCategories = null;

        try {
            Object cacheObj = redisUtil.get(CATEGORY_ALL_KEY);
            if (cacheObj != null) {
                // 安全转换Redis中的LinkedHashMap为List<Category>
                cacheCategories = objectMapper.convertValue(
                        cacheObj,
                        new TypeReference<List<Category>>() {}
                );
                if (!cacheCategories.isEmpty()) {
                    return cacheCategories;
                }
            }
        } catch (Exception e) {
            System.err.println("Redis查询全量分类缓存失败：" + e.getMessage());
            // 清理错误缓存
            redisUtil.delete(CATEGORY_ALL_KEY);
        }

        List<Category> dbCategories = categoryMapper.findAll();

        try {
            if (dbCategories != null && !dbCategories.isEmpty()) {
                redisUtil.set(CATEGORY_ALL_KEY, dbCategories, CACHE_EXPIRE_SECONDS);
            }
        } catch (Exception e) {
            System.err.println("Redis存入全量分类缓存失败：" + e.getMessage());
        }

        return dbCategories != null ? dbCategories : Collections.emptyList();
    }

    /**
     * 获取分类树形结构（含品牌）
     */
    public List<CategoryTreeDTO> getCategoryTreeWithBrands() {
        List<Category> allCategories = findAll();
        if (allCategories.isEmpty()) {
            return Collections.emptyList();
        }

        List<Brand> allBrands = brandMapper.findAllBrands();
        if (allBrands == null) {
            allBrands = Collections.emptyList();
        }

        // 按 category_id 分组品牌（增加空值保护）
        Map<Long, List<Brand>> brandMap = allBrands.stream()
                .filter(brand -> brand.getCategoryId() != null) // 过滤category_id为空的品牌
                .collect(Collectors.groupingBy(Brand::getCategoryId));

        // 按 parent_id 分组分类（此时allCategories已确保是Category类型，不会再强转失败）
        Map<Long, List<Category>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        // 递归构建树形结构
        return buildTree(parentMap, brandMap, 0L);
    }

    /**
     * 递归构建分类树形结构
     */
    private List<CategoryTreeDTO> buildTree(Map<Long, List<Category>> parentMap,
                                            Map<Long, List<Brand>> brandMap,
                                            Long parentId) {
        List<Category> children = parentMap.getOrDefault(parentId, Collections.emptyList());
        return children.stream().map(cat -> {
            CategoryTreeDTO dto = new CategoryTreeDTO();
            dto.setId(cat.getId());
            dto.setName(cat.getName());
            // 递归构建子节点
            dto.setChildren(buildTree(parentMap, brandMap, cat.getId()));
            // 挂载该分类下的品牌（避免空指针）
            dto.setBrands(brandMap.getOrDefault(cat.getId(), Collections.emptyList()));
            return dto;
        }).collect(Collectors.toList());
    }
}