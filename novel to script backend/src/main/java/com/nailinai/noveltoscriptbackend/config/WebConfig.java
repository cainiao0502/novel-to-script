package com.nailinai.noveltoscriptbackend.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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
