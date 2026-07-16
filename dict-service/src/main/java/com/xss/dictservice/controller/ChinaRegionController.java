package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.ChinaRegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChinaRegionController {
    private final ChinaRegionService regionService;

    @GetMapping("/provinces")
    public Result<List<Map<String, Object>>> getProvinces() {
        return Result.ok(regionService.getProvinces());
    }

    @GetMapping("/cities")
    public Result<List<Map<String, Object>>> getCities(@RequestParam(required = false) String province_code) {
        return Result.ok(regionService.getCities(province_code));
    }

    @GetMapping("/districts")
    public Result<List<Map<String, Object>>> getDistricts(@RequestParam(required = false) String city_code) {
        return Result.ok(regionService.getDistricts(city_code));
    }

    @GetMapping("/towns")
    public Result<List<Map<String, Object>>> getTowns(@RequestParam(required = false) String district_code) {
        return Result.ok(regionService.getTowns(district_code));
    }

    @GetMapping("/villages")
    public Result<List<Map<String, Object>>> getVillages(@RequestParam(required = false) String town_code) {
        return Result.ok(regionService.getVillages(town_code));
    }

    @GetMapping("/regions/path")
    public Result<List<Map<String, Object>>> getPath(@RequestParam(required = false) String region_code,
                                                     @RequestParam(required = false) Integer region_level) {
        return Result.ok(regionService.getPath(region_code, region_level));
    }

    @PostMapping("/admin/refresh-regions")
    public Result<Void> refreshRegions() {
        regionService.clearCache();
        return Result.ok(null, "Region cache cleared");
    }
}
