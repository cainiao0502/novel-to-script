package com.nailinai.noveltoscriptbackend.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Sa-Token 鉴权拦截器
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/register",
                        "/user/login",
                        "/user/code/send",
                        "/captcha/**"
                );
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            StringRedisTemplate stringRedisTemplate,
            RateLimitProperties properties) {

        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter(stringRedisTemplate, properties));
        registration.addUrlPatterns("/api/projects/*");
        registration.setOrder(-100);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
