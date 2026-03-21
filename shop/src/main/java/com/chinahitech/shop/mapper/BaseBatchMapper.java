package com.chinahitech.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/*基于 MyBatis-Plus 扩展的通用批量插入 Mapper 接口，核心作用是为所有继承它的 Mapper 提供一个 “只插入非空字段” 的批量插入方法，从而提升批量数据写入的效率和灵活性。*/
public interface BaseBatchMapper<T> extends BaseMapper<T> {

    @Insert("<script>" +
            "INSERT INTO brand (name, category_id, introduction, letter) " +
            "VALUES " +
            "<foreach collection='list' item='item' separator=','> " +
            "(#{item.name}, #{item.categoryId}, #{item.introduction}, #{item.letter}) " +
            "</foreach>" +
            "</script>")
    int insertBatchSomeColumn(@Param("list") List<T> entityList);
}