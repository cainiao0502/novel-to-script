package com.nailinai.noveltoscriptbackend.api.dto.auth;

import lombok.Data;

@Data
public class ApiResult<T> {

    private boolean success;
    private String message;
    private String errorCode;
    private T data;

    private ApiResult() {}

    public static <T> ApiResult<T> ok() {
        ApiResult<T> r = new ApiResult<>();
        r.success = true;
        return r;
    }

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.success = true;
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> fail(String message) {
        ApiResult<T> r = new ApiResult<>();
        r.success = false;
        r.message = message;
        return r;
    }

    public static <T> ApiResult<T> fail(String errorCode, String message) {
        ApiResult<T> r = new ApiResult<>();
        r.success = false;
        r.errorCode = errorCode;
        r.message = message;
        return r;
    }
}
