package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CurrencyController {
    private final CurrencyService currencyService;

    @GetMapping("/currencies")
    public Result<List<Map<String, Object>>> getCurrencies(@RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) String keyword) {
        return Result.ok(currencyService.getCurrencies(status, keyword));
    }

    @GetMapping("/currencies/{currency_code}")
    public Result<Map<String, Object>> getCurrency(@PathVariable("currency_code") String currencyCode) {
        return Result.ok(currencyService.getCurrency(currencyCode));
    }

    @PostMapping("/admin/refresh-currencies")
    public Result<Void> refreshCurrencies() {
        currencyService.clearCache();
        return Result.ok(null, "Currency cache cleared");
    }
}
