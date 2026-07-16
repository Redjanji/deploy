package com.xss.authservice.service;

import com.xss.authservice.dto.JwtResponse;
import com.xss.authservice.dto.LoginRequest;
import com.xss.authservice.dto.RegisterRequest;
import com.xss.authservice.dto.UserInfoDTO;
import com.xss.authservice.entity.UserEntity;

public interface AuthService {

    void register(RegisterRequest request);

    JwtResponse login(LoginRequest request);

    UserEntity getUserById(Long userId);

    UserInfoDTO getUserInfo(Long userId);
}
