package com.nailinai.noveltoscriptbackend.api.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @NotNull(message = "登录类型不能为空")
    @Min(value = 1, message = "登录类型不合法")
    @Max(value = 2, message = "登录类型不合法")
    private Integer type;

    private String password;

    private String verifyCode;
}
