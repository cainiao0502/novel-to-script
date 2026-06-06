package com.nailinai.noveltoscriptbackend.service.auth;

import com.nailinai.noveltoscriptbackend.api.dto.auth.LoginRequest;
import com.nailinai.noveltoscriptbackend.api.dto.auth.LoginResponse;
import com.nailinai.noveltoscriptbackend.api.dto.auth.RegisterRequest;

public interface UserService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout();
}
