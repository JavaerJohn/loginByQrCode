package com.demo.config;

import com.demo.controller.interceptor.ConfirmInterceptor;
import com.demo.controller.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Autowired
    private ConfirmInterceptor confirmInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns("/static/**")
                .addPathPatterns("/getUser", "/login/scan");
        registry.addInterceptor(confirmInterceptor)
                .excludePathPatterns("/static/**")
                .addPathPatterns("/login/confirm");
    }
}
