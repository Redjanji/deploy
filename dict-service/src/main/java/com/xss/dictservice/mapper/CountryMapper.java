package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.Country;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CountryMapper extends BaseMapper<Country> {

    @Select("SELECT country_code, country_code3, numeric_code, name_zh, name_en, " +
            "phone_code, currency_code, continent_code, flag_emoji, sort_order " +
            "FROM v_countries " +
            "WHERE (continent_code = #{cc} OR #{cc} IS NULL) " +
            "AND ((name_zh LIKE CONCAT('%', #{kw}, '%') OR name_en LIKE CONCAT('%', #{kw}, '%')) OR #{kw} IS NULL) " +
            "ORDER BY sort_order, country_code")
    List<Map<String, Object>> selectCountries(@Param("cc") String continentCode,
                                              @Param("kw") String keyword);

    @Select("SELECT country_code, country_code3, numeric_code, name_zh, name_en, " +
            "phone_code, currency_code, continent_code, flag_emoji, sort_order " +
            "FROM v_countries WHERE country_code = #{cc} LIMIT 1")
    Map<String, Object> selectCountryByCode(@Param("cc") String countryCode);
}
