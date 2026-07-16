package com.xss.searchservice.vo;

import lombok.Data;

import java.util.List;

@Data
public class SearchResultVO {
    private List<PropertyDocumentVO> records;
    private Long total;
    private Integer size;
    private Integer current;
    private Integer pages;
}
