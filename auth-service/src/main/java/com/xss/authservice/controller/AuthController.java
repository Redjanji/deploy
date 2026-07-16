package com.xss.authservice.controller;

import com.xss.authservice.dto.JwtResponse;
import com.xss.authservice.dto.LoginRequest;
import com.xss.authservice.dto.RegisterRequest;
import com.xss.authservice.dto.UserInfoDTO;
import com.xss.authservice.security.JwtTokenProvider;
import com.xss.authservice.service.AuthService;
import com.xss.authservice.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("注册成功");
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            tokenBlacklistService.addToBlacklist(token);
        }
        return ResponseEntity.ok("登出成功");
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        tokenBlacklistService.addToBlacklist(token);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String newToken = jwtTokenProvider.generateToken(userId);
        UserInfoDTO user = authService.getUserInfo(userId);
        return ResponseEntity.ok(new JwtResponse(newToken, user.getId(), user.getUsername()));
    }

    @GetMapping("/userinfo")
    public ResponseEntity<UserInfoDTO> userinfo(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(authService.getUserInfo(userId));
    }
}
