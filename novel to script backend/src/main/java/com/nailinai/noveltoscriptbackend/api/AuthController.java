package com.nailinai.noveltoscriptbackend.api;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import com.nailinai.noveltoscriptbackend.api.dto.auth.*;
import com.nailinai.noveltoscriptbackend.service.auth.UserService;
import com.nailinai.noveltoscriptbackend.service.auth.VerifyCodeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private VerifyCodeService verifyCodeService;

    @Autowired
    private ImageCaptchaApplication captchaApplication;

    @Value("${captcha.secondary.keyPrefix:captcha:secondary}")
    private String captchaSecondaryPrefix;

    /** 发送验证码（需先过滑块） */
    @PostMapping("/code/send")
    public ApiResult<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        verifyCaptcha(request.getCaptchaVerification());
        verifyCodeService.sendCode(request.getMobile(), request.getType());
        return ApiResult.ok();
    }

    /** 注册 */
    @PostMapping("/register")
    public ApiResult<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResult.ok();
    }

    /** 登录 */
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ApiResult.ok(response);
    }

    /** 退出 */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        userService.logout();
        return ApiResult.ok();
    }

    /** 滑块验证码二次校验（原子消费，防重放） */
    private void verifyCaptcha(String captchaId) {
        CacheStore cacheStore = captchaApplication.getCacheStore();
        String key = captchaSecondaryPrefix + ":" + captchaId;
        AnyMap cached = cacheStore.getAndRemoveCache(key);
        if (cached == null) {
            throw new RuntimeException("请先完成滑块验证");
        }
    }
}
