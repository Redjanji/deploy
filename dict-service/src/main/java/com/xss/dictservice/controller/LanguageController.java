package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.LanguageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LanguageController {
    private final LanguageService languageService;

    @GetMapping("/languages")
    public Result<List<Map<String, Object>>> getLanguages(@RequestParam(required = false) String keyword) {
        return Result.ok(languageService.getLanguages(keyword));
    }

    @GetMapping("/languages/{lang_code}")
    public Result<Map<String, Object>> getLanguage(@PathVariable("lang_code") String langCode) {
        return Result.ok(languageService.getLanguage(langCode));
    }

    @PostMapping("/admin/refresh-languages")
    public Result<Void> refreshLanguages() {
        languageService.clearCache();
        return Result.ok(null, "Language cache cleared");
    }
}
