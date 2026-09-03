package com.routeplan.contentimport.application;

import com.routeplan.contentimport.domain.ContentSourceType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContentImporterRegistry {
    private final List<ContentImporter> importers;

    public ContentImporterRegistry(List<ContentImporter> importers) {
        this.importers = List.copyOf(importers);
    }

    public ContentImporter get(ContentSourceType type) {
        return importers.stream().filter(importer -> importer.supports(type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 콘텐츠 출처입니다: " + type));
    }
}
