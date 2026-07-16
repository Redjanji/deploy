package com.xss.authservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xss.authservice.dto.JwtResponse;
import com.xss.authservice.dto.LoginRequest;
import com.xss.authservice.dto.RegisterRequest;
import com.xss.authservice.dto.UserInfoDTO;
import com.xss.authservice.entity.UserEntity;
import com.xss.authservice.exception.BusinessException;
import com.xss.authservice.mapper.UserMapper;
import com.xss.authservice.mq.StatsEventPublisher;
import com.xss.authservice.security.JwtTokenProvider;
import com.xss.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StatsEventPublisher statsEventPublisher;

    @Override
    public void register(RegisterRequest request) {
        try {
            UserEntity user = new UserEntity();
            user.setUsername(request.getUsername());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            userMapper.insert(user);
            statsEventPublisher.publish("USER_REGISTER", "default", user.getId(), user.getId());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                throw new BusinessException("用户名已存在");
            }
            throw e;
        }
    }

    @Override
    public JwtResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId());
        statsEventPublisher.publish("USER_LOGIN", "default", user.getId(), user.getId());
        return new JwtResponse(token, user.getId(), user.getUsername());
    }

    @Override
    public UserEntity getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    @Override
    public UserInfoDTO getUserInfo(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return new UserInfoDTO(user.getId(), user.getUsername(), user.getEmail(), user.getPhone());
    }
}
