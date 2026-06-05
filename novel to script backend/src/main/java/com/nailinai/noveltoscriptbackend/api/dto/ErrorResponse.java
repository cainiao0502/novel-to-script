package com.nailinai.noveltoscriptbackend.api.dto;

public class ErrorResponse {
    public int status;
    public String code;
    public String message;
    public String detail;

    public ErrorResponse(int status, String code, String message, String detail) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.detail = detail;
    }

    public static ErrorResponse of(int status, String code, String message) {
        return new ErrorResponse(status, code, message, null);
    }
}
