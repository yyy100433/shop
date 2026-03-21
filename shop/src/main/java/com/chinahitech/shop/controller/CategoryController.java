package com.chinahitech.shop.controller;

import com.chinahitech.shop.bean.Category;
import com.chinahitech.shop.service.CategoryService;
import com.chinahitech.shop.utils.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.chinahitech.shop.bean.CategoryTreeDTO;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/list")
    public Result getCategoryList() {
        return Result.ok();
    }

    @RequestMapping("/all")
    public Result getAll(){
        List<Category> categories = categoryService.findAll();
        return Result.ok().data("items",categories);
    }

    @RequestMapping("/get")
    public Result getByParentId(Long parentId){
        List<Category> categories = categoryService.getByParentId(parentId);
        return Result.ok().data("items",categories);
    }

    @GetMapping("/treeWithBrands")
    public Result getCategoryTreeWithBrands() {
        List<CategoryTreeDTO> tree = categoryService.getCategoryTreeWithBrands();
        return Result.ok().data("tree", tree);
    }
}
