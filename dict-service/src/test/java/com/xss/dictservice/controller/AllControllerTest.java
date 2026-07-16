package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.ChinaRegionService;
import com.xss.dictservice.service.CountryService;
import com.xss.dictservice.service.CurrencyService;
import com.xss.dictservice.service.LanguageService;
import com.xss.dictservice.service.TimezoneService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({
        ChinaRegionController.class,
        CountryController.class,
        CurrencyController.class,
        LanguageController.class,
        TimezoneController.class
})
class AllControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChinaRegionService chinaRegionService;

    @MockBean
    private CountryService countryService;

    @MockBean
    private CurrencyService currencyService;

    @MockBean
    private LanguageService languageService;

    @MockBean
    private TimezoneService timezoneService;

    // ========== 中国行政区划接口测试 ==========

    @Test
    void getProvinces_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("region_code", "440000", "region_name", "广东省")
        );
        when(chinaRegionService.getProvinces()).thenReturn(mockData);

        mockMvc.perform(get("/api/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].region_code").value("440000"));

        verify(chinaRegionService, times(1)).getProvinces();
    }

    @Test
    void getCities_withProvinceCode_shouldReturnFilteredList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("region_code", "440300", "region_name", "深圳市", "province_code", "440000")
        );
        when(chinaRegionService.getCities("440000")).thenReturn(mockData);

        mockMvc.perform(get("/api/cities").param("province_code", "440000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].region_code").value("440300"));

        verify(chinaRegionService, times(1)).getCities("440000");
    }

    @Test
    void getDistricts_withCityCode_shouldReturnFilteredList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("region_code", "440305", "region_name", "南山区", "city_code", "440300")
        );
        when(chinaRegionService.getDistricts("440300")).thenReturn(mockData);

        mockMvc.perform(get("/api/districts").param("city_code", "440300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].region_name").value("南山区"));
    }

    @Test
    void getTowns_withDistrictCode_shouldReturnFilteredList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("region_code", "440305001", "region_name", "南头街道")
        );
        when(chinaRegionService.getTowns("440305")).thenReturn(mockData);

        mockMvc.perform(get("/api/towns").param("district_code", "440305"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getVillages_withTownCode_shouldReturnFilteredList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("region_code", "440305001001", "region_name", "南头城社区")
        );
        when(chinaRegionService.getVillages("440305001")).thenReturn(mockData);

        mockMvc.perform(get("/api/villages").param("town_code", "440305001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getPath_withRegionCode_shouldReturnPath() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("region_code", "440305", "full_path", "广东省 > 深圳市 > 南山区")
        );
        when(chinaRegionService.getPath("440305", null)).thenReturn(mockData);

        mockMvc.perform(get("/api/regions/path").param("region_code", "440305"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].full_path").value("广东省 > 深圳市 > 南山区"));
    }

    @Test
    void refreshRegions_shouldReturnOk() throws Exception {
        doNothing().when(chinaRegionService).clearCache();

        mockMvc.perform(post("/api/admin/refresh-regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Region cache cleared"));

        verify(chinaRegionService, times(1)).clearCache();
    }

    // ========== 国家接口测试 ==========

    @Test
    void getCountries_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("country_code", "CN", "name_zh", "中国", "phone_code", "86")
        );
        when(countryService.getCountries(isNull(), isNull())).thenReturn(mockData);

        mockMvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].country_code").value("CN"));
    }

    @Test
    void getCountries_withContinentAndKeyword_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("country_code", "CN", "name_zh", "中国")
        );
        when(countryService.getCountries("AS", "中国")).thenReturn(mockData);

        mockMvc.perform(get("/api/countries")
                        .param("continent_code", "AS")
                        .param("keyword", "中国"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getCountry_byCode_shouldReturnDetail() throws Exception {
        Map<String, Object> mockData = createMap("country_code", "CN", "name_zh", "中国", "phone_code", "86");
        when(countryService.getCountry("CN")).thenReturn(mockData);

        mockMvc.perform(get("/api/countries/CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.country_code").value("CN"))
                .andExpect(jsonPath("$.data.name_zh").value("中国"));
    }

    @Test
    void refreshCountries_shouldReturnOk() throws Exception {
        doNothing().when(countryService).clearCache();

        mockMvc.perform(post("/api/admin/refresh-countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Country cache cleared"));
    }

    // ========== 货币接口测试 ==========

    @Test
    void getCurrencies_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("currency_code", "CNY", "name_zh", "人民币", "symbol", "¥")
        );
        when(currencyService.getCurrencies(isNull(), isNull())).thenReturn(mockData);

        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].currency_code").value("CNY"))
                .andExpect(jsonPath("$.data[0].symbol").value("¥"));
    }

    @Test
    void getCurrencies_withStatusAndKeyword_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("currency_code", "USD", "name_en", "US Dollar")
        );
        when(currencyService.getCurrencies(1, "USD")).thenReturn(mockData);

        mockMvc.perform(get("/api/currencies")
                        .param("status", "1")
                        .param("keyword", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getCurrency_byCode_shouldReturnDetail() throws Exception {
        Map<String, Object> mockData = createMap("currency_code", "CNY", "name_zh", "人民币");
        when(currencyService.getCurrency("CNY")).thenReturn(mockData);

        mockMvc.perform(get("/api/currencies/CNY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.currency_code").value("CNY"));
    }

    @Test
    void refreshCurrencies_shouldReturnOk() throws Exception {
        doNothing().when(currencyService).clearCache();

        mockMvc.perform(post("/api/admin/refresh-currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Currency cache cleared"));
    }

    // ========== 语言接口测试 ==========

    @Test
    void getLanguages_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("lang_code", "chi", "name_zh", "中文", "native_name", "中文")
        );
        when(languageService.getLanguages(isNull())).thenReturn(mockData);

        mockMvc.perform(get("/api/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].lang_code").value("chi"));
    }

    @Test
    void getLanguages_withKeyword_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("lang_code", "eng", "name_en", "English")
        );
        when(languageService.getLanguages("eng")).thenReturn(mockData);

        mockMvc.perform(get("/api/languages").param("keyword", "eng"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getLanguage_byCode_shouldReturnDetail() throws Exception {
        Map<String, Object> mockData = createMap("lang_code", "chi", "name_zh", "中文");
        when(languageService.getLanguage("chi")).thenReturn(mockData);

        mockMvc.perform(get("/api/languages/chi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name_zh").value("中文"));
    }

    @Test
    void refreshLanguages_shouldReturnOk() throws Exception {
        doNothing().when(languageService).clearCache();

        mockMvc.perform(post("/api/admin/refresh-languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Language cache cleared"));
    }

    // ========== 时区接口测试 ==========

    @Test
    void getTimezones_shouldReturnList() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("timezone_id", "Asia/Shanghai", "offset_utc", "+08:00")
        );
        when(timezoneService.getTimezones(isNull())).thenReturn(mockData);

        mockMvc.perform(get("/api/timezones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].timezone_id").value("Asia/Shanghai"));
    }

    @Test
    void getTimezones_withKeyword_shouldReturnFiltered() throws Exception {
        List<Map<String, Object>> mockData = Arrays.asList(
                createMap("timezone_id", "Asia/Tokyo", "offset_utc", "+09:00")
        );
        when(timezoneService.getTimezones("Tokyo")).thenReturn(mockData);

        mockMvc.perform(get("/api/timezones").param("keyword", "Tokyo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getTimezone_byId_shouldReturnDetail() throws Exception {
        Map<String, Object> mockData = createMap("timezone_id", "Asia/Shanghai", "offset_utc", "+08:00");
        when(timezoneService.getTimezone("Asia/Shanghai")).thenReturn(mockData);

        mockMvc.perform(get("/api/timezones/detail").param("timezone_id", "Asia/Shanghai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.offset_utc").value("+08:00"));
    }

    @Test
    void refreshTimezones_shouldReturnOk() throws Exception {
        doNothing().when(timezoneService).clearCache();

        mockMvc.perform(post("/api/admin/refresh-timezones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Timezone cache cleared"));
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> createMap(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
