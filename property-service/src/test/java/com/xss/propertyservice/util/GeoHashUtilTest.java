package com.xss.propertyservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoHashUtilTest {

    @Test
    @DisplayName("encode: 正确的经纬度编码")
    void encode_shouldReturnCorrectGeoHash() {
        String hash = GeoHashUtil.encode(39.9042, 116.4074, 12);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertEquals(12, hash.length());
    }

    @Test
    @DisplayName("encode: 不同精度的编码长度")
    void encode_shouldReturnCorrectLengthForDifferentPrecision() {
        double lat = 39.9042;
        double lng = 116.4074;

        assertEquals(3, GeoHashUtil.encode(lat, lng, 3).length());
        assertEquals(5, GeoHashUtil.encode(lat, lng, 5).length());
        assertEquals(7, GeoHashUtil.encode(lat, lng, 7).length());
        assertEquals(10, GeoHashUtil.encode(lat, lng, 10).length());
        assertEquals(12, GeoHashUtil.encode(lat, lng, 12).length());
    }

    @Test
    @DisplayName("encode: 相同坐标相同精度应返回相同结果")
    void encode_sameCoordinatesSamePrecision_shouldReturnSameResult() {
        String hash1 = GeoHashUtil.encode(39.9042, 116.4074, 12);
        String hash2 = GeoHashUtil.encode(39.9042, 116.4074, 12);
        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("encode: 不同坐标应返回不同结果")
    void encode_differentCoordinates_shouldReturnDifferentResult() {
        String hash1 = GeoHashUtil.encode(39.9042, 116.4074, 12);
        String hash2 = GeoHashUtil.encode(31.2304, 121.4737, 12);
        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("selectPrecisionByRadius: 各半径区间对应正确精度")
    void selectPrecisionByRadius_shouldReturnCorrectPrecision() {
        assertEquals(7, GeoHashUtil.selectPrecisionByRadius(0.05));
        assertEquals(7, GeoHashUtil.selectPrecisionByRadius(0.1));

        assertEquals(6, GeoHashUtil.selectPrecisionByRadius(0.5));
        assertEquals(6, GeoHashUtil.selectPrecisionByRadius(1.0));

        assertEquals(5, GeoHashUtil.selectPrecisionByRadius(2.0));
        assertEquals(5, GeoHashUtil.selectPrecisionByRadius(5.0));

        assertEquals(4, GeoHashUtil.selectPrecisionByRadius(10.0));
        assertEquals(4, GeoHashUtil.selectPrecisionByRadius(20.0));

        assertEquals(3, GeoHashUtil.selectPrecisionByRadius(50.0));
        assertEquals(3, GeoHashUtil.selectPrecisionByRadius(100.0));
    }

    @Test
    @DisplayName("selectPrecisionByRadius: 半径为0返回最高精度7")
    void selectPrecisionByRadius_zeroRadius_shouldReturnPrecision7() {
        assertEquals(7, GeoHashUtil.selectPrecisionByRadius(0));
    }

    @Test
    @DisplayName("selectPrecisionByRadius: 负半径按0处理返回最高精度7")
    void selectPrecisionByRadius_negativeRadius_shouldReturnPrecision7() {
        assertEquals(7, GeoHashUtil.selectPrecisionByRadius(-1.0));
    }

    @Test
    @DisplayName("getAdjacent: 返回8个邻居")
    void getAdjacent_shouldReturn8Neighbors() {
        String centerHash = GeoHashUtil.encode(39.9042, 116.4074, 6);
        String[] adjacent = GeoHashUtil.getAdjacent(centerHash);

        assertNotNull(adjacent);
        assertEquals(8, adjacent.length);

        for (String neighbor : adjacent) {
            assertNotNull(neighbor);
            assertFalse(neighbor.isEmpty());
            assertEquals(centerHash.length(), neighbor.length());
        }
    }

    @Test
    @DisplayName("getAdjacent: 邻居不应包含中心geohash")
    void getAdjacent_neighborsShouldNotContainCenter() {
        String centerHash = GeoHashUtil.encode(39.9042, 116.4074, 6);
        String[] adjacent = GeoHashUtil.getAdjacent(centerHash);

        for (String neighbor : adjacent) {
            assertNotEquals(centerHash, neighbor);
        }
    }

    @Test
    @DisplayName("getAdjacent: 8个邻居都不重复")
    void getAdjacent_allNeighborsShouldBeUnique() {
        String centerHash = GeoHashUtil.encode(39.9042, 116.4074, 6);
        String[] adjacent = GeoHashUtil.getAdjacent(centerHash);

        long distinctCount = java.util.Arrays.stream(adjacent).distinct().count();
        assertEquals(8, distinctCount);
    }
}
