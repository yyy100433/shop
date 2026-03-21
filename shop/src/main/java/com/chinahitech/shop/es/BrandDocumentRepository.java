package com.chinahitech.shop.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BrandDocumentRepository extends ElasticsearchRepository<BrandDocument, Long> {
}
