package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.ChinaRegion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChinaRegionMapper extends BaseMapper<ChinaRegion> {

    @Select("SELECT region_code, region_name, region_type, sort_order " +
            "FROM v_provinces ORDER BY sort_order")
    List<Map<String, Object>> selectProvinces();

    @Select("SELECT region_code, region_name, region_type, province_code, province_name, sort_order " +
            "FROM v_cities " +
            "WHERE (province_code = #{pc} OR #{pc} IS NULL) ORDER BY province_code, sort_order")
    List<Map<String, Object>> selectCities(@Param("pc") String provinceCode);

    @Select("SELECT region_code, region_name, region_type, city_code, city_name, province_code, province_name, sort_order " +
            "FROM v_districts " +
            "WHERE (city_code = #{cc} OR #{cc} IS NULL) ORDER BY province_code, city_code, sort_order")
    List<Map<String, Object>> selectDistricts(@Param("cc") String cityCode);

    @Select("SELECT region_code, region_name, region_type, district_code, district_name, city_code, city_name, province_code, province_name, sort_order " +
            "FROM v_towns " +
            "WHERE (district_code = #{dc} OR #{dc} IS NULL) ORDER BY province_code, city_code, district_code, sort_order")
    List<Map<String, Object>> selectTowns(@Param("dc") String districtCode);

    @Select("SELECT region_code, region_name, region_type, town_code, town_name, district_code, district_name, city_code, city_name, province_code, province_name, sort_order " +
            "FROM v_villages " +
            "WHERE (town_code = #{tc} OR #{tc} IS NULL) ORDER BY province_code, city_code, district_code, town_code, sort_order")
    List<Map<String, Object>> selectVillages(@Param("tc") String townCode);

    @Select("SELECT region_code, region_name, parent_code, region_level, region_type, full_path, code_path, depth " +
            "FROM v_region_path " +
            "WHERE (region_code = #{rc} OR #{rc} IS NULL) " +
            "AND (region_level = #{lvl} OR #{lvl} IS NULL) ORDER BY depth, region_code")
    List<Map<String, Object>> selectPath(@Param("rc") String regionCode,
                                         @Param("lvl") Integer level);
}
