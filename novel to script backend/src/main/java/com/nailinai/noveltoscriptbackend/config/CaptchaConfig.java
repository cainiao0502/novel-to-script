package com.nailinai.noveltoscriptbackend.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.common.model.dto.ResourceMap;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Override auto-configured Redis resource store with local in-memory store.
 * Manually registers all default captcha resources (background images and
 * slider/rotate templates) from the bundled library classpath files.
 * The DefaultBuiltInResources utility from the library doesn't work here
 * because it uses tag="default" internally while the captcha generator
 * looks up resources with tag=null — causing a tag mismatch.
 */
@Configuration
public class CaptchaConfig {

    @Bean
    @Primary
    public ResourceStore localResourceStore() {
        LocalMemoryResourceStore store = new LocalMemoryResourceStore();

        // ---- Background images (no tag, so generator can find them) ----
        store.addResource(CaptchaTypeConstant.SLIDER,
                new Resource("classpath", "META-INF/cut-image/resource/1.jpg"));

        // ---- Slider template 1 (key names must match generator constants: "fixed.png", "active.png") ----
        ResourceMap slider1 = new ResourceMap();
        slider1.put("fixed.png", new Resource("classpath",
                "META-INF/cut-image/template/slider_1/fixed.png"));
        slider1.put("active.png", new Resource("classpath",
                "META-INF/cut-image/template/slider_1/active.png"));
        store.addTemplate(CaptchaTypeConstant.SLIDER, slider1);

        // ---- Slider template 2 ----
        ResourceMap slider2 = new ResourceMap();
        slider2.put("fixed.png", new Resource("classpath",
                "META-INF/cut-image/template/slider_2/fixed.png"));
        slider2.put("active.png", new Resource("classpath",
                "META-INF/cut-image/template/slider_2/active.png"));
        store.addTemplate(CaptchaTypeConstant.SLIDER, slider2);

        return store;
    }
}
