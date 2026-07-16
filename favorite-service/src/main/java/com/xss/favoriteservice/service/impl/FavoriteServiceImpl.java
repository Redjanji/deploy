package com.xss.favoriteservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xss.favoriteservice.common.BusinessException;
import com.xss.favoriteservice.entity.UserFavoriteEntity;
import com.xss.favoriteservice.mapper.UserFavoriteMapper;
import com.xss.favoriteservice.service.FavoriteService;
import com.xss.favoriteservice.vo.FavoriteVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavoriteEntity> implements FavoriteService {

    @Override
    public List<FavoriteVO> listFavorites(Long userId) {
        requireUser(userId);
        LambdaQueryWrapper<UserFavoriteEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavoriteEntity::getUserId, userId)
                .orderByDesc(UserFavoriteEntity::getCreatedAt);
        List<UserFavoriteEntity> entities = baseMapper.selectList(wrapper);
        return entities.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public void addFavorite(Long userId, Long propertyId) {
        requireUser(userId);
        requirePropertyId(propertyId);
        // 幂等：已收藏则直接返回
        if (isFavorited(userId, propertyId)) {
            return;
        }
        UserFavoriteEntity entity = new UserFavoriteEntity();
        entity.setUserId(userId);
        entity.setPropertyId(propertyId);
        baseMapper.insert(entity);
    }

    @Override
    public boolean isFavorited(Long userId, Long propertyId) {
        if (userId == null || propertyId == null) {
            return false;
        }
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<UserFavoriteEntity>()
                .eq(UserFavoriteEntity::getUserId, userId)
                .eq(UserFavoriteEntity::getPropertyId, propertyId));
        return count != null && count > 0;
    }

    @Override
    public void removeFavorite(Long userId, Long propertyId) {
        requireUser(userId);
        requirePropertyId(propertyId);
        // 幂等：不存在则删除 0 行，不报错
        baseMapper.delete(new LambdaQueryWrapper<UserFavoriteEntity>()
                .eq(UserFavoriteEntity::getUserId, userId)
                .eq(UserFavoriteEntity::getPropertyId, propertyId));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }
    }

    private void requirePropertyId(Long propertyId) {
        if (propertyId == null) {
            throw new BusinessException(400, "房源ID不能为空");
        }
    }

    private FavoriteVO convertToVO(UserFavoriteEntity entity) {
        FavoriteVO vo = new FavoriteVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
