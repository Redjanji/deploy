package com.xss.dictservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xss.dictservice.entity.Language;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LanguageMapper extends BaseMapper<Language> {

    @Select("SELECT lang_code, name_zh, name_en, native_name " +
            "FROM sys_language " +
            "WHERE ((name_zh LIKE CONCAT('%', #{kw}, '%') OR name_en LIKE CONCAT('%', #{kw}, '%') OR lang_code LIKE CONCAT('%', #{kw}, '%')) OR #{kw} IS NULL) " +
            "ORDER BY lang_code")
    List<Map<String, Object>> selectLanguages(@Param("kw") String keyword);

    @Select("SELECT lang_code, name_zh, name_en, native_name " +
            "FROM sys_language WHERE lang_code = #{lc} LIMIT 1")
    Map<String, Object> selectLanguageByCode(@Param("lc") String langCode);
}
