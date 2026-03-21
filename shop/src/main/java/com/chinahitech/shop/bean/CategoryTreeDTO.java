package com.chinahitech.shop.bean;

import lombok.Data;
import java.util.List;

@Data
public class CategoryTreeDTO {

    private Long id;
    private String name;
    private List<CategoryTreeDTO> children;
    private List<Brand> brands; // 该分类下的品牌列表
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CategoryTreeDTO> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeDTO> children) {
        this.children = children;
    }

    public List<Brand> getBrands() {
        return brands;
    }

    public void setBrands(List<Brand> brands) {
        this.brands = brands;
    }



    @Override
    public String toString() {
        return "CategoryTreeDTO{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", children=" + children +
                ", brands=" + brands +
                '}';
    }

}