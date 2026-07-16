package com.xss.analyticsservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xss.analyticsservice.common.Result;
import com.xss.analyticsservice.service.StatsQueryService;
import com.xss.analyticsservice.vo.DashboardSummaryVO;
import com.xss.analyticsservice.vo.ImageUploadSummaryVO;
import com.xss.analyticsservice.vo.PropertyViewStatsVO;
import com.xss.analyticsservice.vo.UserActionStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StatsControllerTest {

    @Mock
    private StatsQueryService statsQueryService;

    @InjectMocks
    private StatsController statsController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String appId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(statsController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        appId = "test-app";
        today = LocalDate.now();
    }

    @Test
    @DisplayName("GET /api/stats/property/views: 获取房产浏览统计成功")
    void getPropertyViews_shouldReturnSuccess() throws Exception {
        List<PropertyViewStatsVO> mockData = new ArrayList<>();
        PropertyViewStatsVO vo = new PropertyViewStatsVO();
        vo.setAppId(appId);
        vo.setPropertyId(1L);
        vo.setStatsDate(today);
        vo.setViewCount(100L);
        vo.setUniqueVisitors(50L);
        mockData.add(vo);

        when(statsQueryService.getPropertyViews(anyString(), anyString(), anyString())).thenReturn(mockData);

        mockMvc.perform(get("/api/stats/property/views")
                        .param("appId", appId)
                        .param("startDate", today.minusDays(7).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].appId").value(appId))
                .andExpect(jsonPath("$.data[0].propertyId").value(1))
                .andExpect(jsonPath("$.data[0].viewCount").value(100))
                .andExpect(jsonPath("$.data[0].uniqueVisitors").value(50));
    }

    @Test
    @DisplayName("GET /api/stats/property/views: 不传endDate时使用默认值")
    void getPropertyViews_withoutEndDate_shouldUseDefault() throws Exception {
        List<PropertyViewStatsVO> mockData = Collections.emptyList();

        when(statsQueryService.getPropertyViews(anyString(), anyString(), any())).thenReturn(mockData);

        mockMvc.perform(get("/api/stats/property/views")
                        .param("appId", appId)
                        .param("startDate", today.minusDays(7).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /api/stats/image/upload-summary: 获取图片上传统计成功")
    void getImageUploadSummary_shouldReturnSuccess() throws Exception {
        List<ImageUploadSummaryVO> mockData = new ArrayList<>();
        ImageUploadSummaryVO vo = new ImageUploadSummaryVO();
        vo.setAppId(appId);
        vo.setStatsDate(today);
        vo.setUploadCount(10L);
        vo.setTotalSize(102400L);
        vo.setTotalSizeFormatted("100.00 KB");
        mockData.add(vo);

        when(statsQueryService.getImageUploadSummary(anyString())).thenReturn(mockData);

        mockMvc.perform(get("/api/stats/image/upload-summary")
                        .param("appId", appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].appId").value(appId))
                .andExpect(jsonPath("$.data[0].uploadCount").value(10))
                .andExpect(jsonPath("$.data[0].totalSize").value(102400))
                .andExpect(jsonPath("$.data[0].totalSizeFormatted").value("100.00 KB"));
    }

    @Test
    @DisplayName("GET /api/stats/image/upload-summary: 不传appId时查询全部")
    void getImageUploadSummary_withoutAppId_shouldReturnAll() throws Exception {
        List<ImageUploadSummaryVO> mockData = new ArrayList<>();
        ImageUploadSummaryVO vo1 = new ImageUploadSummaryVO();
        vo1.setAppId("app1");
        vo1.setStatsDate(today);
        vo1.setUploadCount(10L);
        vo1.setTotalSize(102400L);
        vo1.setTotalSizeFormatted("100.00 KB");
        mockData.add(vo1);

        ImageUploadSummaryVO vo2 = new ImageUploadSummaryVO();
        vo2.setAppId("app2");
        vo2.setStatsDate(today);
        vo2.setUploadCount(20L);
        vo2.setTotalSize(204800L);
        vo2.setTotalSizeFormatted("200.00 KB");
        mockData.add(vo2);

        when(statsQueryService.getImageUploadSummary(any())).thenReturn(mockData);

        mockMvc.perform(get("/api/stats/image/upload-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/stats/user/actions: 获取用户行为统计成功")
    void getUserActions_shouldReturnSuccess() throws Exception {
        List<UserActionStatsVO> mockData = new ArrayList<>();
        UserActionStatsVO vo = new UserActionStatsVO();
        vo.setAppId(appId);
        vo.setEventType("USER_LOGIN");
        vo.setStatsDate(today);
        vo.setActionCount(50L);
        mockData.add(vo);

        when(statsQueryService.getUserActions(any(), any(), any(), any())).thenReturn(mockData);

        mockMvc.perform(get("/api/stats/user/actions")
                        .param("appId", appId)
                        .param("eventType", "USER_LOGIN")
                        .param("startDate", today.minusDays(7).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].appId").value(appId))
                .andExpect(jsonPath("$.data[0].eventType").value("USER_LOGIN"))
                .andExpect(jsonPath("$.data[0].actionCount").value(50));
    }

    @Test
    @DisplayName("GET /api/stats/user/actions: 不传可选参数时使用默认值")
    void getUserActions_withoutOptionalParams_shouldUseDefaults() throws Exception {
        List<UserActionStatsVO> mockData = Collections.emptyList();

        when(statsQueryService.getUserActions(any(), any(), any(), any())).thenReturn(mockData);

        mockMvc.perform(get("/api/stats/user/actions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /api/stats/dashboard: 获取仪表盘总览成功")
    void getDashboard_shouldReturnSuccess() throws Exception {
        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setToday(today);
        vo.setTodayPropertyViews(100L);
        vo.setTodayImageUploads(20L);
        vo.setTodayUserRegisters(10L);
        vo.setTodayUserLogins(50L);
        vo.setTodayPropertyCreates(5L);
        vo.setTopProperties(new ArrayList<>());
        vo.setAppImageSummary(new ArrayList<>());

        when(statsQueryService.getDashboard()).thenReturn(vo);

        mockMvc.perform(get("/api/stats/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.todayPropertyViews").value(100))
                .andExpect(jsonPath("$.data.todayImageUploads").value(20))
                .andExpect(jsonPath("$.data.todayUserRegisters").value(10))
                .andExpect(jsonPath("$.data.todayUserLogins").value(50))
                .andExpect(jsonPath("$.data.todayPropertyCreates").value(5))
                .andExpect(jsonPath("$.data.topProperties").isArray())
                .andExpect(jsonPath("$.data.appImageSummary").isArray());
    }

    @Test
    @DisplayName("GET /api/stats/dashboard: 空数据时返回正确结构")
    void getDashboard_emptyData_shouldReturnCorrectStructure() throws Exception {
        DashboardSummaryVO vo = new DashboardSummaryVO();
        vo.setToday(today);
        vo.setTodayPropertyViews(0L);
        vo.setTodayImageUploads(0L);
        vo.setTodayUserRegisters(0L);
        vo.setTodayUserLogins(0L);
        vo.setTodayPropertyCreates(0L);
        vo.setTopProperties(Collections.emptyList());
        vo.setAppImageSummary(Collections.emptyList());

        when(statsQueryService.getDashboard()).thenReturn(vo);

        mockMvc.perform(get("/api/stats/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.todayPropertyViews").value(0))
                .andExpect(jsonPath("$.data.todayImageUploads").value(0))
                .andExpect(jsonPath("$.data.todayUserRegisters").value(0))
                .andExpect(jsonPath("$.data.todayUserLogins").value(0))
                .andExpect(jsonPath("$.data.todayPropertyCreates").value(0))
                .andExpect(jsonPath("$.data.topProperties").isEmpty())
                .andExpect(jsonPath("$.data.appImageSummary").isEmpty());
    }
}
