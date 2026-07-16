package com.xss.searchservice.controller;

import com.xss.searchservice.common.Result;
import com.xss.searchservice.dto.PropertySearchRequest;
import com.xss.searchservice.service.PropertySearchService;
import com.xss.searchservice.vo.SearchResultVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final PropertySearchService searchService;

    public SearchController(@Lazy PropertySearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/properties")
    public Result<SearchResultVO> search(PropertySearchRequest request,
                                         @RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId) {
        return Result.success(searchService.search(request, appId));
    }

    @PostMapping("/admin/reindex")
    public Result<Void> reindex(@RequestHeader(value = "X-App-Id", required = false, defaultValue = "default") String appId) {
        searchService.reindexAll(appId);
        return Result.success();
    }
}
