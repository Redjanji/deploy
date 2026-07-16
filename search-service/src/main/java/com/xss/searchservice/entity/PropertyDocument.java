package com.xss.searchservice.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

@Data
@Document(indexName = "properties", createIndex = true)
public class PropertyDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private String appId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(type = FieldType.Integer)
    private Integer rentalArea;

    @Field(type = FieldType.Keyword)
    private String rooms;

    @Field(type = FieldType.Keyword)
    private String orientation;

    @Field(type = FieldType.Keyword)
    private String floor;

    @Field(type = FieldType.Integer)
    private Integer totalFloors;

    @Field(type = FieldType.Text)
    private String address;

    @GeoPointField
    private GeoPoint location;

    @Field(type = FieldType.Keyword)
    private String provinceCode;

    @Field(type = FieldType.Text, index = false)
    private String provinceName;

    @Field(type = FieldType.Keyword)
    private String cityCode;

    @Field(type = FieldType.Text, index = false)
    private String cityName;

    @Field(type = FieldType.Keyword)
    private String districtCode;

    @Field(type = FieldType.Text, index = false)
    private String districtName;

    @Field(type = FieldType.Keyword)
    private String decoration;

    @Field(type = FieldType.Keyword)
    private String heatingMethod;

    @Field(type = FieldType.Keyword)
    private String waterSupply;

    @Field(type = FieldType.Keyword)
    private String powerSupply;

    @Field(type = FieldType.Keyword)
    private String gasSupply;

    @Field(type = FieldType.Keyword)
    private String internet;

    @Field(type = FieldType.Keyword)
    private String tvService;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Boolean)
    private Boolean hot;

    @Field(type = FieldType.Boolean)
    private Boolean featured;

    @Field(type = FieldType.Integer)
    private Integer publishStatus;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss||uuuu-MM-dd||epoch_millis")
    private String createdAt;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss||uuuu-MM-dd||epoch_millis")
    private String updatedAt;

    @Field(type = FieldType.Keyword, index = false)
    private String coverUrl;

    private Double lat;
    private Double lon;
}
