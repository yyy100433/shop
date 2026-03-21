package com.chinahitech.shop.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chinahitech.shop.bean.Brand;
import com.chinahitech.shop.mapper.BrandMapper;
import com.chinahitech.shop.utils.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class BrandService {
    @Autowired
    private BrandMapper brandMapper;

    @Resource
    private RedisUtil redisUtil;

    // 注入异步线程池（用于延迟删缓存/异步批量写缓存）
    @Resource
    private ThreadPoolExecutor asyncDeleteCacheExecutor;

    // 引入Jackson工具类进行安全的类型转换（全局单例）
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 缓存Key前缀（保持不变）
    private static final String BRAND_INFO_KEY_PREFIX = "brand:info:";        // 单条品牌缓存Key前缀
    private static final String BRAND_CATEGORY_KEY_PREFIX = "brand:category:";// 分类关联品牌缓存Key前缀
    private static final String BRAND_PAGE_KEY_PREFIX = "brand:page:";        // 分页品牌缓存Key前缀
    private static final String BRAND_PAGE_TOTAL_KEY = "brand:page:total";    // 品牌总数缓存Key
    // 搜索缓存Key前缀
    private static final String BRAND_SEARCH_KEY_PREFIX = "brand:search:";        // 搜索品牌缓存Key前缀
    private static final String BRAND_SEARCH_TOTAL_PREFIX = "brand:search:total:";// 搜索总数缓存Key前缀
    // 缓存过期时间：改为秒（适配 RedisUtil 的 set 方法参数）
    private static final long CACHE_EXPIRE_SECONDS = 30 * 60;                // 30分钟 = 1800秒
    private static final long DELAY_DELETE_TIME = 500;                        // 延迟删缓存时间（毫秒）

    public Page<Brand> findAll(Page<Brand> page) {
        Long current = page.getCurrent();
        Long size = page.getSize();
        String pageCacheKey = BRAND_PAGE_KEY_PREFIX + current + ":" + size;
        Page<Brand> cachePage = null;
        Long total = null;

        try {
            // 1. 从Redis读取缓存（不再直接反序列化Page对象）
            Object cacheObj = redisUtil.get(pageCacheKey);
            if (cacheObj != null) {
                // 核心修改：先转换为Map，提取分页核心字段，避免Page类的序列化问题
                Map<String, Object> pageMap = objectMapper.convertValue(cacheObj, new TypeReference<Map<String, Object>>() {});

                // 手动构建Page对象，只使用核心字段，忽略isSearchCount等无关字段
                cachePage = new Page<>();
                cachePage.setCurrent(Long.parseLong(pageMap.get("current").toString()));
                cachePage.setSize(Long.parseLong(pageMap.get("size").toString()));

                // 转换records列表（核心数据）
                List<Brand> records = objectMapper.convertValue(
                        pageMap.get("records"),
                        new TypeReference<List<Brand>>() {}
                );
                cachePage.setRecords(records);

                System.out.println("分页缓存命中并转换成功：" + pageCacheKey);
            }

            // 2. 读取总条数缓存
            Object totalObj = redisUtil.get(BRAND_PAGE_TOTAL_KEY);
            if (totalObj != null) {
                total = Long.parseLong(totalObj.toString());
            }

            // 3. 缓存命中则返回
            if (cachePage != null && total != null) {
                cachePage.setTotal(total);
                return cachePage;
            }
        } catch (Exception e) {
            System.err.println("Redis分页缓存处理失败，降级查数据库：" + e.getMessage());
            // 清理错误缓存
            redisUtil.delete(pageCacheKey);
            redisUtil.delete(BRAND_PAGE_TOTAL_KEY);
        }

        // 4. 缓存未命中，查询数据库
        Page<Brand> dbPage = brandMapper.selectPage(page, null);
        total = dbPage.getTotal();

        // 5. 异步写入缓存（只缓存核心字段，不直接存Page对象）
        asyncBatchSetCache(pageCacheKey, dbPage, BRAND_PAGE_TOTAL_KEY, total);

        return dbPage;
    }

    // 同步修改异步写入缓存的方法（只缓存核心字段）
    private void asyncBatchSetCache(String pageCacheKey, Page<Brand> dbPage, String totalKey, Long total) {
        asyncDeleteCacheExecutor.execute(() -> {
            try {
                // 核心修改：构建只包含核心字段的Map，避免Page类的多余字段
                Map<String, Object> pageData = new HashMap<>();
                pageData.put("current", dbPage.getCurrent());
                pageData.put("size", dbPage.getSize());
                pageData.put("records", dbPage.getRecords()); // 只存核心数据

                // 写入精简后的分页数据
                redisUtil.set(pageCacheKey, pageData, CACHE_EXPIRE_SECONDS);
                // 写入总条数缓存
                redisUtil.set(totalKey, total.toString(), CACHE_EXPIRE_SECONDS);
            } catch (Exception e) {
                System.err.println("异步批量写入分页缓存失败：" + e.getMessage());
            }
        });
    }

    public int add(Brand brand, Long categoryId) {
        // 参数校验（保持不变）
        if (brand != null && brand.getLetter() != null) {
            String letter = brand.getLetter().trim();
            if (letter.length() > 1) {
                throw new IllegalArgumentException("品牌评级只能输入1个字符（当前输入：" + letter + "）");
            }
            brand.setLetter(letter.toUpperCase());
        }

        try {
            // 第一步：删除关联缓存（写操作→主节点，延迟双删第一步）
            String categoryCacheKey = BRAND_CATEGORY_KEY_PREFIX + categoryId;
            deleteRelatedCache(categoryCacheKey);

            // 第二步：操作数据库
            int brandResult = brandMapper.insert(brand);
            if (brandResult <= 0) {
                return 0;
            }

            // 第三步：异步写入单条品牌缓存
            asyncDeleteCacheExecutor.execute(() -> {
                try {
                    String brandCacheKey = BRAND_INFO_KEY_PREFIX + brand.getId();
                    // 写入Redis主节点，设置过期时间（秒）
                    redisUtil.set(brandCacheKey, brand, CACHE_EXPIRE_SECONDS);
                } catch (Exception e) {
                    System.err.println("异步写入品牌缓存失败：" + e.getMessage());
                }
            });

            // 第四步：延迟双删第二步（避免缓存脏数据）
            delayDeleteCache(() -> {
                deleteRelatedCache(categoryCacheKey);
                //清除所有搜索缓存
                deleteSearchCache();
            });

            return 1;
        } catch (Exception e) {
            System.err.println("添加品牌失败：" + e.getMessage());
            throw new RuntimeException("添加品牌失败：" + e.getMessage(), e);
        }
    }

    public int delete(Long brandId) {
        if (brandId == null || brandId < 1) {
            throw new IllegalArgumentException("品牌ID不能为空且必须为正数");
        }

        try {
            Brand brand = brandMapper.selectById(brandId);
            if (brand == null) {
                return 0;
            }

            String brandCacheKey = BRAND_INFO_KEY_PREFIX + brandId;
            redisUtil.delete(brandCacheKey);
            deleteRelatedCache(null); // 删除分类、分页相关缓存

            int deleteResult = brandMapper.deleteById(brandId);

            if (deleteResult > 0) {
                delayDeleteCache(() -> {
                    redisUtil.delete(brandCacheKey);
                    deleteRelatedCache(null);
                    //清除所有搜索缓存
                    deleteSearchCache();
                    System.out.println("延迟双删：删除品牌缓存完成");
                });
            }

            return deleteResult;
        } catch (Exception e) {
            System.err.println("删除品牌失败：" + e.getMessage());
            throw new RuntimeException("删除品牌失败：" + e.getMessage(), e);
        }
    }

    public int edit(Brand brand) {
        if (brand == null || brand.getId() == null || brand.getId() < 1) {
            throw new IllegalArgumentException("品牌ID不能为空且必须为正数");
        }

        if (brand.getLetter() != null) {
            String letter = brand.getLetter().trim();
            if (letter.length() > 1) {
                throw new IllegalArgumentException("品牌评级只能输入1个字符（当前输入：" + letter + "）");
            }
            brand.setLetter(letter.toUpperCase());
        }

        try {
            Long brandId = brand.getId();
            String brandCacheKey = BRAND_INFO_KEY_PREFIX + brandId;

            redisUtil.delete(brandCacheKey);
            deleteRelatedCache(null);

            int updateResult = brandMapper.updateById(brand);

            if (updateResult > 0) {
                asyncDeleteCacheExecutor.execute(() -> {
                    try {
                        redisUtil.set(brandCacheKey, brand, CACHE_EXPIRE_SECONDS);
                    } catch (Exception e) {
                        System.err.println("异步更新品牌缓存失败：" + e.getMessage());
                    }
                });

                delayDeleteCache(() -> {
                    redisUtil.delete(brandCacheKey);
                    deleteRelatedCache(null);
                    //清除所有搜索缓存
                    deleteSearchCache();
                    System.out.println("延迟双删：编辑品牌缓存完成");
                });
            }

            return updateResult;
        } catch (Exception e) {
            System.err.println("编辑品牌失败：" + e.getMessage());
            throw new RuntimeException("编辑品牌失败：" + e.getMessage(), e);
        }
    }

    public Brand findById(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("品牌ID不能为空且必须为正数");
        }

        String cacheKey = BRAND_INFO_KEY_PREFIX + id;
        Brand cacheBrand = null;
        try {
            Object cacheObj = redisUtil.get(cacheKey);
            if (cacheObj != null) {
                // 安全转换LinkedHashMap为Brand对象
                cacheBrand = objectMapper.convertValue(cacheObj, Brand.class);
                return cacheBrand;
            }
        } catch (Exception e) {
            System.err.println("Redis查询品牌缓存失败，降级查数据库：" + e.getMessage());
            redisUtil.delete(cacheKey); // 清理错误缓存
        }

        Brand dbBrand = brandMapper.selectById(id);
        if (dbBrand != null) {
            asyncDeleteCacheExecutor.execute(() -> {
                try {
                    redisUtil.set(cacheKey, dbBrand, CACHE_EXPIRE_SECONDS);
                } catch (Exception e) {
                    System.err.println("写入品牌缓存失败：" + e.getMessage());
                }
            });
        }

        return dbBrand;
    }

    public List<Brand> getByCategoryId(Long categoryId) {
        if (categoryId == null || categoryId < 1) {
            throw new IllegalArgumentException("分类ID不能为空且必须为正数");
        }

        String cacheKey = BRAND_CATEGORY_KEY_PREFIX + categoryId;
        List<Brand> cacheBrandList = null;
        try {
            Object cacheObj = redisUtil.get(cacheKey);
            if (cacheObj != null) {
                // 安全转换LinkedHashMap为List<Brand>
                cacheBrandList = objectMapper.convertValue(
                        cacheObj,
                        new TypeReference<List<Brand>>() {}
                );
                return cacheBrandList;
            }
        } catch (Exception e) {
            System.err.println("Redis查询分类品牌缓存失败，降级查数据库：" + e.getMessage());
            redisUtil.delete(cacheKey); // 清理错误缓存
        }

        List<Brand> brandList = brandMapper.selectByCategoryId(categoryId);
        // 空值保护
        if (brandList == null) {
            brandList = Collections.emptyList();
        }

        if (!brandList.isEmpty()) {
            List<Brand> finalBrandList = brandList;
            asyncDeleteCacheExecutor.execute(() -> {
                try {
                    redisUtil.set(cacheKey, finalBrandList, CACHE_EXPIRE_SECONDS);
                } catch (Exception e) {
                    System.err.println("写入分类品牌缓存失败：" + e.getMessage());
                }
            });
        }

        return brandList;
    }

    /**
     * 通用延迟删缓存方法
     */
    private void delayDeleteCache(Runnable deleteTask) {
        asyncDeleteCacheExecutor.execute(() -> {
            try {
                Thread.sleep(DELAY_DELETE_TIME);
                deleteTask.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("延迟删缓存线程中断：" + e.getMessage());
            } catch (Exception e) {
                System.err.println("延迟删缓存执行失败：" + e.getMessage());
            }
        });
    }

    /**
     * 删除关联缓存（统一处理分类、分页缓存）
     * @param categoryCacheKey 分类缓存Key（可为null，null则删除所有分类/分页缓存）
     */
    private void deleteRelatedCache(String categoryCacheKey) {
        try {
            // 1. 删除分类关联缓存（若有）
            if (categoryCacheKey != null) {
                redisUtil.delete(categoryCacheKey);
            }
            // 2. 删除分页总数缓存
            redisUtil.delete(BRAND_PAGE_TOTAL_KEY);
            // 3. 删除所有分页缓存（使用通配符）
            redisUtil.delete(BRAND_PAGE_KEY_PREFIX + "*");
        } catch (Exception e) {
            System.err.println("删除关联缓存失败：" + e.getMessage());
        }
    }

    //按名称模糊搜索
    public Page<Brand> searchByName(String keyword, Page<Brand> page) {
        // 如果关键词为空，返回空结果
        if (keyword == null || keyword.trim().isEmpty()) {
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        }

        String trimmedKeyword = keyword.trim();
        // 先尝试从缓存中获取（如果缓存中有相关数据）
        String searchCacheKey = BRAND_SEARCH_KEY_PREFIX + trimmedKeyword + ":" + page.getCurrent() + ":" + page.getSize();
        String searchTotalKey = BRAND_SEARCH_TOTAL_PREFIX + trimmedKeyword;

        try {
            Object cacheObj = redisUtil.get(searchCacheKey);
            Object totalObj = redisUtil.get(searchTotalKey);

            if (cacheObj != null && totalObj != null) {
                Map<String, Object> pageMap = objectMapper.convertValue(cacheObj, new TypeReference<Map<String, Object>>() {});
                Page<Brand> cachePage = new Page<>();
                cachePage.setCurrent(Long.parseLong(pageMap.get("current").toString()));
                cachePage.setSize(Long.parseLong(pageMap.get("size").toString()));

                List<Brand> records = objectMapper.convertValue(
                        pageMap.get("records"),
                        new TypeReference<List<Brand>>() {}
                );
                cachePage.setRecords(records);
                cachePage.setTotal(Long.parseLong(totalObj.toString()));

                System.out.println("搜索缓存命中：" + searchCacheKey);
                return cachePage;
            }
        } catch (Exception e) {
            System.err.println("搜索缓存读取失败：" + e.getMessage());
            redisUtil.delete(searchCacheKey);
            redisUtil.delete(searchTotalKey);
        }

        // 缓存未命中，查询数据库
        List<Brand> records = brandMapper.searchByName(trimmedKeyword, page);
        Long total = brandMapper.countSearchByName(trimmedKeyword);

        page.setRecords(records);
        page.setTotal(total);

        // 异步写入缓存
        if (!records.isEmpty()) {
            asyncBatchSetSearchCache(searchCacheKey, page, searchTotalKey, total);
        }

        return page;
    }

    //异步写入搜索缓存
    private void asyncBatchSetSearchCache(String pageCacheKey, Page<Brand> dbPage, String totalKey, Long total) {
        asyncDeleteCacheExecutor.execute(() -> {
            try {
                Map<String, Object> pageData = new HashMap<>();
                pageData.put("current", dbPage.getCurrent());
                pageData.put("size", dbPage.getSize());
                pageData.put("records", dbPage.getRecords());

                redisUtil.set(pageCacheKey, pageData, CACHE_EXPIRE_SECONDS);
                redisUtil.set(totalKey, total.toString(), CACHE_EXPIRE_SECONDS);
                System.out.println("搜索缓存写入成功：" + pageCacheKey);
            } catch (Exception e) {
                System.err.println("异步写入搜索缓存失败：" + e.getMessage());
            }
        });
    }

    //删除所有搜索缓存
    private void deleteSearchCache() {
        try {
            // 由于Redis没有通配符删除，这里我们清理常用的搜索缓存key
            // 实际项目中可以考虑使用Redis的keys命令或SCAN命令
            // 这里简单起见，我们只删除一些常用的搜索缓存
            redisUtil.delete(BRAND_SEARCH_KEY_PREFIX + "*");
            redisUtil.delete(BRAND_SEARCH_TOTAL_PREFIX + "*");
            System.out.println("已清除所有搜索缓存");
        } catch (Exception e) {
            System.err.println("删除搜索缓存失败：" + e.getMessage());
        }
    }
}