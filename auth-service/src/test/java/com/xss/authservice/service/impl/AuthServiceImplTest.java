package com.xss.authservice.service.impl;

import com.xss.authservice.dto.JwtResponse;
import com.xss.authservice.dto.LoginRequest;
import com.xss.authservice.dto.RegisterRequest;
import com.xss.authservice.dto.UserInfoDTO;
import com.xss.authservice.entity.UserEntity;
import com.xss.authservice.exception.BusinessException;
import com.xss.authservice.mapper.UserMapper;
import com.xss.authservice.mq.StatsEventPublisher;
import com.xss.authservice.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private StatsEventPublisher statsEventPublisher;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password123");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPhone("13800138000");

        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");

        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("testuser");
        userEntity.setPasswordHash("encodedPassword");
        userEntity.setEmail("test@example.com");
        userEntity.setPhone("13800138000");
    }

    @Test
    void register_success() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userMapper.insert(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        authService.register(registerRequest);

        verify(userMapper).insert(any(UserEntity.class));
        verify(statsEventPublisher).publish(eq("USER_REGISTER"), eq("default"), eq(1L), eq(1L));
    }

    @Test
    void register_usernameDuplicate_throwsBusinessException() {
        when(userMapper.selectOne(any())).thenReturn(userEntity);

        assertThrows(BusinessException.class, () -> authService.register(registerRequest));

        verify(userMapper, never()).insert(any(UserEntity.class));
        verify(statsEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    void login_success() {
        when(userMapper.selectOne(any())).thenReturn(userEntity);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L)).thenReturn("test-token");

        JwtResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("test-token", response.getToken());
        assertEquals(1L, response.getUserId());
        assertEquals("testuser", response.getUsername());
        verify(statsEventPublisher).publish(eq("USER_LOGIN"), eq("default"), eq(1L), eq(1L));
    }

    @Test
    void login_userNotFound_throwsBadCredentialsException() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtTokenProvider, never()).generateToken(anyLong());
        verify(statsEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    void login_wrongPassword_throwsBadCredentialsException() {
        when(userMapper.selectOne(any())).thenReturn(userEntity);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));

        verify(jwtTokenProvider, never()).generateToken(anyLong());
        verify(statsEventPublisher, never()).publish(anyString(), anyString(), any(), any());
    }

    @Test
    void getUserById_exists_returnsUserEntity() {
        when(userMapper.selectById(1L)).thenReturn(userEntity);

        UserEntity result = authService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void getUserById_notFound_returnsNull() {
        when(userMapper.selectById(999L)).thenReturn(null);

        UserEntity result = authService.getUserById(999L);

        assertNull(result);
    }

    @Test
    void getUserInfo_exists_returnsUserInfoDTO() {
        when(userMapper.selectById(1L)).thenReturn(userEntity);

        UserInfoDTO result = authService.getUserInfo(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("13800138000", result.getPhone());
    }

    @Test
    void getUserInfo_notFound_returnsNull() {
        when(userMapper.selectById(999L)).thenReturn(null);

        UserInfoDTO result = authService.getUserInfo(999L);

        assertNull(result);
    }
}
