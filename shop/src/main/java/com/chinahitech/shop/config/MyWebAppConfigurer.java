package com.chinahitech.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MyWebAppConfigurer implements WebMvcConfigurer {
    //将后端接口路径 /upload/** 映射到服务器本地的文件目录，让前端能通过 URL 访问本地上传的文件。
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**").addResourceLocations("file:"+System.getProperty("user.dir")+"/upload/");
    }
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的前端源
        config.addAllowedOrigin("http://localhost:9528");
        //允许所有 HTTP 请求方法
        config.addAllowedMethod("*");
        //允许前端请求携带所有请求头
        config.addAllowedHeader("*");
        //允许前端携带 Cookie / 认证信息
        config.setAllowCredentials(true);
        //对后端所有接口（/**）生效这个跨域配置。
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
