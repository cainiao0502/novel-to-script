package com.nailinai.noveltoscriptbackend.service.auth;

public interface VerifyCodeService {

    String SCENE_REGISTER = "register";
    String SCENE_LOGIN = "login";

    static String getScene(Integer type) {
        return switch (type) {
            case 1 -> SCENE_REGISTER;
            case 2 -> SCENE_LOGIN;
            default -> throw new IllegalArgumentException("无效的验证码类型: " + type);
        };
    }

    void sendCode(String mobile, Integer type);

    void verifyCode(String mobile, String code, Integer type);

    void deleteCode(String mobile, Integer type);
}
