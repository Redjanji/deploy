package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.TimezoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TimezoneController {
    private final TimezoneService timezoneService;

    @GetMapping("/timezones")
    public Result<List<Map<String, Object>>> getTimezones(@RequestParam(required = false) String keyword) {
        return Result.ok(timezoneService.getTimezones(keyword));
    }

    @GetMapping("/timezones/detail")
    public Result<Map<String, Object>> getTimezone(@RequestParam("timezone_id") String timezoneId) {
        return Result.ok(timezoneService.getTimezone(timezoneId));
    }

    @PostMapping("/admin/refresh-timezones")
    public Result<Void> refreshTimezones() {
        timezoneService.clearCache();
        return Result.ok(null, "Timezone cache cleared");
    }
}
