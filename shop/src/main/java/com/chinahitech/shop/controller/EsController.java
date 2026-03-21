package com.chinahitech.shop.controller;

import com.chinahitech.shop.es.BrandDocument;
import com.chinahitech.shop.service.EsService;
import com.chinahitech.shop.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/es")
@CrossOrigin
public class EsController {

    @Autowired
    private EsService esSearchService;

    @GetMapping("/search/brand")
    public Result searchBrand(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        List<BrandDocument> brands = esSearchService.searchBrands(keyword, pageNum, pageSize);
        return Result.ok().data("items", brands);
    }

    @GetMapping("/search/brand/category")
    public Result searchBrandByCategory(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        List<BrandDocument> brands = esSearchService.searchBrandsByCategory(categoryId, pageNum, pageSize);
        return Result.ok().data("items", brands);
    }
}