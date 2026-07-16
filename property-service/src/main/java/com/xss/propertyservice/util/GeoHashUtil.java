package com.xss.propertyservice.util;

import ch.hsr.geohash.GeoHash;

public class GeoHashUtil {

    /**
     * 编码经纬度为 GeoHash 字符串
     *
     * @param lat       纬度
     * @param lng       经度
     * @param precision 精度（字符数），推荐 12（约 3.7 米）
     */
    public static String encode(double lat, double lng, int precision) {
        return GeoHash.withCharacterPrecision(lat, lng, precision).toBase32();
    }

    /**
     * 获取指定 GeoHash 的周围 8 个邻居（用于边界搜索）
     */
    public static String[] getAdjacent(String geohash) {
        GeoHash hash = GeoHash.fromGeohashString(geohash);
        GeoHash[] adjacent = hash.getAdjacent();
        String[] result = new String[adjacent.length];
        for (int i = 0; i < adjacent.length; i++) {
            result[i] = adjacent[i].toBase32();
        }
        return result;
    }

    /**
     * 根据半径选择合适的 GeoHash 前缀长度
     */
    public static int selectPrecisionByRadius(double radiusKm) {
        if (radiusKm <= 0.1) return 7;   // 约 153m × 153m
        if (radiusKm <= 1) return 6;     // 约 1.2km × 0.6km
        if (radiusKm <= 5) return 5;     // 约 4.9km × 4.9km
        if (radiusKm <= 20) return 4;    // 约 39km × 19.5km
        return 3;                        // 约 156km × 156km
    }
}
