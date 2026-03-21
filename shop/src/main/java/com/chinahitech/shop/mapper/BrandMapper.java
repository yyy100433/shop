package com.chinahitech.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chinahitech.shop.bean.Brand;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
// 关键：继承自定义的 BaseBatchMapper 而非原生 BaseMapper
public interface BrandMapper extends BaseBatchMapper<Brand> {

    @Select("select id, name from brand where category_id = #{categoryId}")
    List<Brand> selectByCategoryId(Long categoryId);

    //一次性查询所有品牌
    @Select("select id, name, category_id, introduction, letter from brand")
    List<Brand> findAllBrands();

    //按名称模糊搜索（带分页）
    @Select("SELECT * FROM brand WHERE name LIKE CONCAT('%', #{keyword}, '%') ORDER BY id")
    List<Brand> searchByName(@Param("keyword") String keyword, Page<Brand> page);

    //统计搜索结果数量
    @Select("SELECT COUNT(*) FROM brand WHERE name LIKE CONCAT('%', #{keyword}, '%')")
    Long countSearchByName(@Param("keyword") String keyword);
}