package com.nailinai.noveltoscriptbackend.api.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SendCodeRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    @NotNull(message = "验证码类型不能为空")
    @Min(value = 1, message = "验证码类型不合法")
    @Max(value = 2, message = "验证码类型不合法")
    private Integer type;

    @NotBlank(message = "请先完成滑块验证")
    private String captchaVerification;
}
