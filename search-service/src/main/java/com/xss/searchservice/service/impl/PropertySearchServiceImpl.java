package com.xss.searchservice.service.impl;

import com.xss.searchservice.common.Result;
import com.xss.searchservice.dto.PropertySearchRequest;
import com.xss.searchservice.entity.PropertyDocument;
import com.xss.searchservice.service.PropertySearchService;
import com.xss.searchservice.vo.PropertyDocumentVO;
import com.xss.searchservice.vo.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PropertySearchServiceImpl implements PropertySearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final RestTemplate restTemplate;

    @Value("${property-service.base-url:http://127.0.0.1:8085}")
    private String propertyServiceUrl;

    public PropertySearchServiceImpl(ElasticsearchOperations elasticsearchOperations, RestTemplate restTemplate) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.restTemplate = restTemplate;
    }

    @Override
    public SearchResultVO search(PropertySearchRequest request, String appId) {
        ensureIndexExists();
        
        Criteria criteria = buildCriteria(request, appId);
        Query query = new CriteriaQuery(criteria);
        
        List<Sort.Order> orders = new ArrayList<>();
        orders.add(Sort.Order.desc("createdAt"));
        query.addSort(Sort.by(orders));
        
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize());
        query.setPageable(pageable);

        SearchHits<PropertyDocument> hits = elasticsearchOperations.search(query, PropertyDocument.class);
        return convertToResult(hits, request);
    }

    private void ensureIndexExists() {
        try {
            if (!elasticsearchOperations.indexOps(PropertyDocument.class).exists()) {
                log.info("Creating Elasticsearch index: properties");
                elasticsearchOperations.indexOps(PropertyDocument.class).createWithMapping();
            }
        } catch (Exception e) {
            log.error("Failed to create index", e);
        }
    }

    private Criteria buildCriteria(PropertySearchRequest request, String appId) {
        Criteria criteria = new Criteria("appId").is(appId)
                .and("publishStatus").is(1)
                .and("status").is(1);

        if (StringUtils.isNotBlank(request.getKeyword())) {
            Criteria keywordCriteria = new Criteria("title").contains(request.getKeyword())
                    .or(new Criteria("description").contains(request.getKeyword()))
                    .or(new Criteria("address").contains(request.getKeyword()));
            criteria = criteria.and(keywordCriteria);
        }

        if (StringUtils.isNotBlank(request.getCityCode())) {
            criteria = criteria.and("cityCode").is(request.getCityCode());
        }

        if (StringUtils.isNotBlank(request.getType())) {
            criteria = criteria.and("type").is(request.getType());
        }

        if (StringUtils.isNotBlank(request.getRooms())) {
            criteria = criteria.and("rooms").is(request.getRooms());
        }

        if (request.getMinPrice() != null) {
            criteria = criteria.and("price").greaterThanEqual(request.getMinPrice());
        }

        if (request.getMaxPrice() != null) {
            criteria = criteria.and("price").lessThanEqual(request.getMaxPrice());
        }

        if (request.getHot() != null) {
            criteria = criteria.and("hot").is(request.getHot());
        }

        if (request.getFeatured() != null) {
            criteria = criteria.and("featured").is(request.getFeatured());
        }

        return criteria;
    }

    private SearchResultVO convertToResult(SearchHits<PropertyDocument> hits, PropertySearchRequest request) {
        SearchResultVO result = new SearchResultVO();
        result.setTotal(hits.getTotalHits());
        result.setSize(request.getSize());
        result.setCurrent(request.getPage());
        result.setPages((int) Math.ceil((double) hits.getTotalHits() / request.getSize()));

        result.setRecords(hits.stream().map(hit -> {
            PropertyDocument doc = hit.getContent();
            PropertyDocumentVO vo = new PropertyDocumentVO();
            vo.setId(doc.getId());
            vo.setAppId(doc.getAppId());
            vo.setTitle(doc.getTitle());
            vo.setType(doc.getType());
            vo.setPrice(doc.getPrice());
            vo.setRentalArea(doc.getRentalArea());
            vo.setRooms(doc.getRooms());
            vo.setOrientation(doc.getOrientation());
            vo.setFloor(doc.getFloor());
            vo.setAddress(doc.getAddress());
            vo.setLat(doc.getLat());
            vo.setLon(doc.getLon());
            vo.setProvinceName(doc.getProvinceName());
            vo.setCityName(doc.getCityName());
            vo.setDistrictName(doc.getDistrictName());
            vo.setDecoration(doc.getDecoration());
            vo.setDescription(doc.getDescription());
            vo.setHot(doc.getHot());
            vo.setFeatured(doc.getFeatured());
            vo.setCoverUrl(doc.getCoverUrl());
            return vo;
        }).collect(Collectors.toList()));

        return result;
    }

    @Override
    public void indexProperty(Long propertyId) {
        indexProperty(propertyId, null);
    }

    private void indexProperty(Long propertyId, String appId) {
        try {
            String url = propertyServiceUrl + "/api/properties/" + propertyId;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (appId != null) {
                headers.set("X-App-Id", appId);
            }
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<Result> respEntity = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, Result.class);

            Result resp = respEntity.getBody();
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                Map<String, Object> data = (Map<String, Object>) resp.getData();
                PropertyDocument doc = convertToDocument(data);
                elasticsearchOperations.save(doc);
                log.info("Indexed property: {}", propertyId);
            } else {
                log.warn("Property not found: {}", propertyId);
            }
        } catch (Exception e) {
            log.error("Failed to index property: {}", propertyId, e);
        }
    }

    @Override
    public void deleteProperty(Long propertyId) {
        try {
            elasticsearchOperations.delete(String.valueOf(propertyId), PropertyDocument.class);
            log.info("Deleted property from index: {}", propertyId);
        } catch (Exception e) {
            log.error("Failed to delete property from index: {}", propertyId, e);
        }
    }

    @Override
    public void syncProperty(Long propertyId, String appId) {
        ensureIndexExists();
        indexProperty(propertyId, appId);
    }

    @Override
    public void deleteProperty(Long propertyId, String appId) {
        deleteProperty(propertyId);
    }

    @Override
    public void reindexAll(String appId) {
        log.info("Starting reindex for appId: {}", appId);
        int page = 1;
        int size = 100;
        int totalIndexed = 0;

        while (true) {
            try {
                String url = propertyServiceUrl + "/api/properties?page=" + page + "&size=" + size;
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                if (appId != null) {
                    headers.set("X-App-Id", appId);
                }
                org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
                org.springframework.http.ResponseEntity<Result> respEntity = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, entity, Result.class);

                Result resp = respEntity.getBody();
                if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
                    break;
                }

                Map<String, Object> data = (Map<String, Object>) resp.getData();
                List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");

                if (records == null || records.isEmpty()) {
                    break;
                }

                for (Map<String, Object> record : records) {
                    Long propertyId = ((Number) record.get("id")).longValue();
                    indexProperty(propertyId, appId);
                    totalIndexed++;
                }

                Long total = ((Number) data.get("total")).longValue();
                if (totalIndexed >= total) {
                    break;
                }

                page++;
            } catch (Exception e) {
                log.error("Failed to reindex page {}: {}", page, e);
                break;
            }
        }

        log.info("Reindex completed, total indexed: {}", totalIndexed);
    }

    private PropertyDocument convertToDocument(Map<String, Object> data) {
        PropertyDocument doc = new PropertyDocument();
        doc.setId(((Number) data.get("id")).longValue());
        doc.setAppId((String) data.get("appId"));
        doc.setTitle((String) data.get("title"));
        doc.setType((String) data.get("type"));
        doc.setPrice(data.get("price") != null ? ((Number) data.get("price")).longValue() : null);
        doc.setRentalArea(data.get("rentalArea") != null ? ((Number) data.get("rentalArea")).intValue() : null);
        doc.setRooms((String) data.get("rooms"));
        doc.setOrientation((String) data.get("orientation"));
        doc.setFloor((String) data.get("floor"));
        doc.setTotalFloors(data.get("totalFloors") != null ? ((Number) data.get("totalFloors")).intValue() : null);
        doc.setAddress((String) data.get("address"));

        Double lat = data.get("lat") != null ? ((Number) data.get("lat")).doubleValue() : null;
        Double lon = data.get("lng") != null ? ((Number) data.get("lng")).doubleValue() : null;
        doc.setLat(lat);
        doc.setLon(lon);

        if (lat != null && lon != null) {
            doc.setLocation(new org.springframework.data.elasticsearch.core.geo.GeoPoint(lat, lon));
        }

        doc.setProvinceCode((String) data.get("provinceCode"));
        doc.setProvinceName((String) data.get("provinceName"));
        doc.setCityCode((String) data.get("cityCode"));
        doc.setCityName((String) data.get("cityName"));
        doc.setDistrictCode((String) data.get("districtCode"));
        doc.setDistrictName((String) data.get("districtName"));
        doc.setDecoration((String) data.get("decoration"));
        doc.setHeatingMethod((String) data.get("heatingMethod"));
        doc.setWaterSupply((String) data.get("waterSupply"));
        doc.setPowerSupply((String) data.get("powerSupply"));
        doc.setGasSupply((String) data.get("gasSupply"));
        doc.setInternet((String) data.get("internet"));
        doc.setTvService((String) data.get("tvService"));
        doc.setDescription((String) data.get("description"));
        doc.setHot((Boolean) data.get("hot"));
        doc.setFeatured((Boolean) data.get("featured"));
        doc.setPublishStatus(data.get("publishStatus") != null ? ((Number) data.get("publishStatus")).intValue() : null);
        doc.setStatus(data.get("status") != null ? ((Number) data.get("status")).intValue() : null);
        doc.setCoverUrl((String) data.get("coverUrl"));

        Object createdAtObj = data.get("createdAt");
        if (createdAtObj != null) {
            doc.setCreatedAt(createdAtObj.toString());
        }

        Object updatedAtObj = data.get("updatedAt");
        if (updatedAtObj != null) {
            doc.setUpdatedAt(updatedAtObj.toString());
        }

        return doc;
    }
}
