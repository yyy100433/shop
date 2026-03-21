package com.chinahitech.shop.service;

import com.chinahitech.shop.es.BrandDocument;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EsService {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 搜索品牌（支持中文分词）
     */
    public List<BrandDocument> searchBrands(String keyword, int pageNum, int pageSize) {
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.multiMatchQuery(keyword)
                        .field("name", 2.0f)    // 品牌名称权重更高
                        .field("introduction")
                        .field("categoryName"))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .withSort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .build();

        SearchHits<BrandDocument> searchHits = elasticsearchRestTemplate.search(
                searchQuery, BrandDocument.class);

        return searchHits.stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
    }

    /**
     * 按分类搜索品牌
     */
    public List<BrandDocument> searchBrandsByCategory(Long categoryId, int pageNum, int pageSize) {
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders.termQuery("categoryId", categoryId))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<BrandDocument> searchHits = elasticsearchRestTemplate.search(
                searchQuery, BrandDocument.class);

        return searchHits.stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
    }
}