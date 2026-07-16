package com.xss.favoriteservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xss.favoriteservice.entity.UserFavoriteEntity;
import com.xss.favoriteservice.vo.FavoriteVO;

import java.util.List;

public interface FavoriteService extends IService<UserFavoriteEntity> {

    List<FavoriteVO> listFavorites(Long userId);

    void addFavorite(Long userId, Long propertyId);

    boolean isFavorited(Long userId, Long propertyId);

    void removeFavorite(Long userId, Long propertyId);
}
