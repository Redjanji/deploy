package com.xss.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.authservice.dto.JwtResponse;
import com.xss.authservice.dto.LoginRequest;
import com.xss.authservice.dto.RegisterRequest;
import com.xss.authservice.dto.UserInfoDTO;
import com.xss.authservice.mapper.UserMapper;
import com.xss.authservice.security.JwtTokenProvider;
import com.xss.authservice.service.AuthService;
import com.xss.authservice.service.TokenBlacklistService;
import com.xss.authservice.config.SecurityConfig;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = {DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEmail("test@example.com");
        request.setPhone("13800138000");

        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("注册成功"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        JwtResponse jwtResponse = new JwtResponse("test-token", 1L, "testuser");
        when(authService.login(any(LoginRequest.class))).thenReturn(jwtResponse);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void logout_success() throws Exception {
        String token = "test-token";
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("type")).thenReturn("user");

        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(jwtTokenProvider.parseToken(token)).thenReturn(claims);
        doNothing().when(tokenBlacklistService).addToBlacklist(anyString());

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("登出成功"));

        verify(tokenBlacklistService).addToBlacklist(token);
    }

    @Test
    @WithMockUser(username = "1")
    void userinfo_authenticated_returnsUserInfo() throws Exception {
        UserInfoDTO userInfo = new UserInfoDTO(1L, "testuser", "test@example.com", "13800138000");
        when(authService.getUserInfo(1L)).thenReturn(userInfo);

        mockMvc.perform(get("/auth/userinfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.phone").value("13800138000"));

        verify(authService).getUserInfo(1L);
    }
}
