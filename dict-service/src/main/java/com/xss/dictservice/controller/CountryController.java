package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.CountryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CountryController {
    private final CountryService countryService;

    @GetMapping("/countries")
    public Result<List<Map<String, Object>>> getCountries(@RequestParam(required = false) String continent_code,
                                                          @RequestParam(required = false) String keyword) {
        return Result.ok(countryService.getCountries(continent_code, keyword));
    }

    @GetMapping("/countries/{country_code}")
    public Result<Map<String, Object>> getCountry(@PathVariable("country_code") String countryCode) {
        return Result.ok(countryService.getCountry(countryCode));
    }

    @PostMapping("/admin/refresh-countries")
    public Result<Void> refreshCountries() {
        countryService.clearCache();
        return Result.ok(null, "Country cache cleared");
    }
}
