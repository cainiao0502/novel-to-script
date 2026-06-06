package com.nailinai.noveltoscriptbackend.api;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.response.ApiResponse;
import com.nailinai.noveltoscriptbackend.api.dto.auth.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Autowired
    private ImageCaptchaApplication captchaApplication;

    @Value("${captcha.secondary.keyPrefix:captcha:secondary}")
    private String captchaSecondaryPrefix;

    @Value("${captcha.secondary.expire:120000}")
    private long captchaSecondaryExpire;

    @GetMapping("/get")
    public ApiResult<Map<String, Object>> get() {
        ApiResponse<ImageCaptchaVO> response = captchaApplication.generateCaptcha();
        ImageCaptchaVO vo = response.getData();

        Map<String, Object> result = new HashMap<>();
        result.put("id", vo.getId());
        result.put("backgroundImage", vo.getBackgroundImage());
        result.put("sliderImage", vo.getTemplateImage());
        result.put("backgroundImageWidth", vo.getBackgroundImageWidth());
        result.put("backgroundImageHeight", vo.getBackgroundImageHeight());
        result.put("templateImageWidth", vo.getTemplateImageWidth());
        result.put("templateImageHeight", vo.getTemplateImageHeight());
        return ApiResult.ok(result);
    }

    @PostMapping("/check")
    public ApiResult<Map<String, String>> check(@RequestBody Map<String, Object> params) {
        String id = (String) params.get("id");
        Object xObj = params.get("x");
        if (id == null || xObj == null) {
            return ApiResult.fail("参数不完整");
        }

        Float x;
        if (xObj instanceof Number) {
            x = ((Number) xObj).floatValue();
        } else {
            x = Float.parseFloat(xObj.toString());
        }

        boolean matched = captchaApplication.matching(id, x);
        if (!matched) {
            return ApiResult.fail("验证失败");
        }

        // Secondary verification: store captchaVerification in cache
        CacheStore cacheStore = captchaApplication.getCacheStore();
        String key = captchaSecondaryPrefix + ":" + id;
        AnyMap data = new AnyMap();
        data.put("id", id);
        cacheStore.setCache(key, data, captchaSecondaryExpire, TimeUnit.MILLISECONDS);

        Map<String, String> result = new HashMap<>();
        result.put("id", id);
        result.put("captchaVerification", id);
        return ApiResult.ok(result);
    }
}
