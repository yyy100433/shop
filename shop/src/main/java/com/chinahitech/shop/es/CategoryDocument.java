package com.chinahitech.shop.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "category_index")
public class CategoryDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;

    @Field(type = FieldType.Long)
    private Long parentId;

    @Field(type = FieldType.Boolean)
    private Boolean isParent;

    @Field(type = FieldType.Integer)
    private Integer sort;

    public CategoryDocument() {}

    public CategoryDocument(com.chinahitech.shop.bean.Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.parentId = category.getParentId();
        this.isParent = category.getIsParent();
        this.sort = category.getSort();
    }
}