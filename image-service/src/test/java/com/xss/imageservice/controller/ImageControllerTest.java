package com.xss.imageservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.imageservice.model.vo.ImageVO;
import com.xss.imageservice.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ImageController imageController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(imageController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void upload_success_returnsOk() throws Exception {
        ImageVO imageVO = ImageVO.builder()
                .id(1L)
                .url("/api/images/test.webp")
                .originUrl("/api/images/test.webp")
                .width(800)
                .height(600)
                .fileSize(1024L)
                .mimeType("image/webp")
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "test-image-data".getBytes()
        );

        when(imageService.upload(any(), eq("test-app"), eq(1L))).thenReturn(imageVO);

        mockMvc.perform(multipart("/api/images/upload")
                        .file(file)
                        .header("X-App-Id", "test-app")
                        .header("X-Owner-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.width").value(800))
                .andExpect(jsonPath("$.data.height").value(600));

        verify(imageService, times(1)).upload(any(), eq("test-app"), eq(1L));
    }

    @Test
    void list_success_returnsImageList() throws Exception {
        ImageVO image1 = ImageVO.builder()
                .id(1L)
                .url("/api/images/img1.webp")
                .originUrl("/api/images/img1.webp")
                .width(800)
                .height(600)
                .fileSize(1024L)
                .mimeType("image/webp")
                .build();

        ImageVO image2 = ImageVO.builder()
                .id(2L)
                .url("/api/images/img2.webp")
                .originUrl("/api/images/img2.webp")
                .width(400)
                .height(300)
                .fileSize(512L)
                .mimeType("image/webp")
                .build();

        when(imageService.listByApp(eq("test-app"), eq(1L), eq(1), eq(20)))
                .thenReturn(List.of(image1, image2));

        mockMvc.perform(get("/api/images")
                        .header("X-App-Id", "test-app")
                        .header("X-Owner-Id", "1")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));

        verify(imageService, times(1)).listByApp("test-app", 1L, 1, 20);
    }

    @Test
    void list_withoutHeaders_usesDefaults() throws Exception {
        when(imageService.listByApp(isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(imageService, times(1)).listByApp(null, null, 1, 20);
    }

    @Test
    void delete_success_returnsOk() throws Exception {
        doNothing().when(imageService).delete(1L, "test-app", 1L);

        mockMvc.perform(delete("/api/images/1")
                        .header("X-App-Id", "test-app")
                        .header("X-Owner-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(imageService, times(1)).delete(1L, "test-app", 1L);
    }
}
