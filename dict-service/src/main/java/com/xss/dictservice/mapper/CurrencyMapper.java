package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.Currency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CurrencyMapper extends BaseMapper<Currency> {

    @Select("SELECT currency_code, name_zh, name_en, symbol, status " +
            "FROM sys_currency " +
            "WHERE (status = #{st} OR #{st} IS NULL) " +
            "AND ((name_zh LIKE CONCAT('%', #{kw}, '%') OR name_en LIKE CONCAT('%', #{kw}, '%') OR currency_code LIKE CONCAT('%', #{kw}, '%')) OR #{kw} IS NULL) " +
            "ORDER BY currency_code")
    List<Map<String, Object>> selectCurrencies(@Param("st") Integer status,
                                               @Param("kw") String keyword);

    @Select("SELECT currency_code, name_zh, name_en, symbol, status " +
            "FROM sys_currency WHERE currency_code = #{cc} LIMIT 1")
    Map<String, Object> selectCurrencyByCode(@Param("cc") String currencyCode);
}
