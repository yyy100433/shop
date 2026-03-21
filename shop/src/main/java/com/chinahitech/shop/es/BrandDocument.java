package com.chinahitech.shop.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "brand_index")  // ES索引名
public class BrandDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String introduction;

    @Field(type = FieldType.Keyword)
    private String letter;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    // 无参构造（Spring Data需要）
    public BrandDocument() {}

    // 从Brand实体转换的构造方法
    public BrandDocument(com.chinahitech.shop.bean.Brand brand, String categoryName) {
        this.id = brand.getId();
        this.name = brand.getName();
        this.introduction = brand.getIntroduction();
        this.letter = brand.getLetter();
        this.categoryId = brand.getCategoryId();
        this.categoryName = categoryName;
    }
}