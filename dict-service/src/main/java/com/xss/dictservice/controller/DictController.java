package com.xss.dictservice.controller;

import com.xss.dictservice.common.Result;
import com.xss.dictservice.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/types")
    public Result<List<String>> getDictTypes() {
        return Result.ok(dictService.getAllDictTypes());
    }

    @GetMapping("/{dictType}/list")
    public Result<List<Map<String, Object>>> getDictList(
            @PathVariable String dictType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.getDictList(dictType, status, keyword));
    }

    @GetMapping("/{dictType}/item/{code}")
    public Result<Map<String, Object>> getDictItem(
            @PathVariable String dictType,
            @PathVariable String code) {
        return Result.ok(dictService.getDictItem(dictType, code));
    }

    @GetMapping("/items")
    public Result<List<Map<String, Object>>> getPropertyDictItems(
            @RequestParam String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.getPropertyDictItems(type, status, keyword));
    }

    @GetMapping("/items/{type}/{itemKey}")
    public Result<Map<String, Object>> getPropertyDictItem(
            @PathVariable String type,
            @PathVariable String itemKey) {
        return Result.ok(dictService.getPropertyDictItem(type, itemKey));
    }

    @GetMapping("/property-types")
    public Result<List<Map<String, Object>>> getAllPropertyDictTypes() {
        return Result.ok(dictService.getAllPropertyDictTypes());
    }

    @GetMapping("/{dictType}/tree")
    public Result<List<Map<String, Object>>> getTreeDict(
            @PathVariable String dictType,
            @RequestParam(required = false) String parent_code,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String keyword) {
        return Result.ok(dictService.getTreeDictList(dictType, parent_code, level, keyword));
    }

    @PostMapping("/admin/refresh/{dictType}")
    public Result<Void> refreshDict(@PathVariable String dictType) {
        dictService.clearDictCache(dictType);
        return Result.ok(null, dictType + " 字典缓存已清除");
    }

    @PostMapping("/admin/refresh-all")
    public Result<Void> refreshAllDicts() {
        List<String> types = dictService.getAllDictTypes();
        for (String type : types) {
            dictService.clearDictCache(type);
        }
        return Result.ok(null, "所有字典缓存已清除");
    }
}
