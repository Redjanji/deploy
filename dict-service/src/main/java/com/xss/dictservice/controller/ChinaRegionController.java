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
    public Result<List<Map<String, Object>>> getCities(@RequestParam(required = false) String provinceCode) {
        return Result.ok(regionService.getCities(provinceCode));
    }

    @GetMapping("/districts")
    public Result<List<Map<String, Object>>> getDistricts(@RequestParam(required = false) String cityCode) {
        return Result.ok(regionService.getDistricts(cityCode));
    }

    @GetMapping("/towns")
    public Result<List<Map<String, Object>>> getTowns(@RequestParam(required = false) String districtCode) {
        return Result.ok(regionService.getTowns(districtCode));
    }

    @GetMapping("/villages")
    public Result<List<Map<String, Object>>> getVillages(@RequestParam(required = false) String townCode) {
        return Result.ok(regionService.getVillages(townCode));
    }

    @GetMapping("/regions/path")
    public Result<List<Map<String, Object>>> getPath(@RequestParam(required = false) String regionCode,
                                                     @RequestParam(required = false) Integer regionLevel) {
        return Result.ok(regionService.getPath(regionCode, regionLevel));
    }

    @PostMapping("/admin/refresh-regions")
    public Result<Void> refreshRegions() {
        regionService.clearCache();
        return Result.ok(null, "Region cache cleared");
    }
}
