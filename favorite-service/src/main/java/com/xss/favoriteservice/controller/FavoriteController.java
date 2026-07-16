package com.xss.favoriteservice.controller;

import com.xss.favoriteservice.common.Result;
import com.xss.favoriteservice.service.FavoriteService;
import com.xss.favoriteservice.vo.FavoriteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public Result<List<FavoriteVO>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(favoriteService.listFavorites(userId));
    }

    @PostMapping("/{propertyId}")
    public Result<Void> add(@PathVariable Long propertyId,
                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        favoriteService.addFavorite(userId, propertyId);
        return Result.ok(null, "收藏成功");
    }

    @GetMapping("/check/{propertyId}")
    public Result<Map<String, Boolean>> check(@PathVariable Long propertyId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Map<String, Boolean> data = new HashMap<>();
        data.put("favorited", favoriteService.isFavorited(userId, propertyId));
        return Result.ok(data);
    }

    @DeleteMapping("/{propertyId}")
    public Result<Void> remove(@PathVariable Long propertyId,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        favoriteService.removeFavorite(userId, propertyId);
        return Result.ok(null, "取消收藏成功");
    }
}
