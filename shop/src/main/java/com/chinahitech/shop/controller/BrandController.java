package com.chinahitech.shop.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chinahitech.shop.bean.Brand;
import com.chinahitech.shop.service.BrandService;
import com.chinahitech.shop.utils.Result;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/brand")
@CrossOrigin
public class BrandController {

    @Autowired
    private BrandService brandService;

    //接收前端传来的分页页码参数 pageNum，如果前端没有传这个参数，defaultValue = "1" 会自动设置默认值为第 1 页
    @RequestMapping("/all")
    public Result getAll(@RequestParam(defaultValue = "1") int pageNum) {

        Page<Brand> page = new Page(pageNum, 10);

        Page<Brand> brands = brandService.findAll(page);
        System.out.println(brands);
        return Result.ok().data("items", brands);
    }

    @RequestMapping("/search")
    public Result search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum) {

        if (keyword == null || keyword.trim().isEmpty()) {
            return getAll(pageNum);
        }

        Page<Brand> page = new Page<>(pageNum, 10);

        try {
            Long id = Long.parseLong(keyword.trim());
            Brand brand = brandService.findById(id);
            if (brand != null) {
                Page<Brand> singlePage = new Page<>(pageNum, 10);
                // 修复：使用 new ArrayList<>() 替代 List.of()
                List<Brand> brandList = new ArrayList<>();
                brandList.add(brand);
                singlePage.setRecords(brandList);
                singlePage.setTotal(1);
                return Result.ok().data("items", singlePage);
            }
        } catch (NumberFormatException e) {
            // 不是数字，忽略，继续按名称搜索
        }

        Page<Brand> brands = brandService.searchByName(keyword, page);
        return Result.ok().data("items", brands);
    }


    @RequestMapping("/get")
    public Result getById(Long id) {
        Brand brand = brandService.findById(id);
        return Result.ok().data("brand", brand);
    }

    @RequestMapping("/add")
    public Result add(Brand brand, @RequestParam Long categoryId) {
        int r = brandService.add(brand, categoryId);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    public Result deleteById(@PathVariable Long id) {
        try {
            brandService.delete(id);
            return Result.ok().message("删除成功");
        } catch (Exception e) {
            return Result.error().message("删除失败：" + e.getMessage());
        }
    }

    @RequestMapping("/getByCategoryId")
    public Result getBrandsByCategoryId(@RequestParam Long categoryId) {

        List<Brand> brandList = brandService.getByCategoryId(categoryId);

        return Result.ok().data("brandList", brandList);
    }

    @RequestMapping("/edit")
    public Result edit(Brand brand) {
        int r = brandService.edit(brand);
        return Result.ok();
    }
}
