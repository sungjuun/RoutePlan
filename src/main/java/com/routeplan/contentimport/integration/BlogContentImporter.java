package com.routeplan.contentimport.integration;

import com.routeplan.contentimport.application.ContentImporter;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.integration.google.ExternalProviderException;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class BlogContentImporter implements ContentImporter {
    private final GenericWebContentImporter web;

    public BlogContentImporter(GenericWebContentImporter web) { this.web = web; }

    @Override
    public boolean supports(ContentSourceType sourceType) { return sourceType == ContentSourceType.BLOG; }

    @Override
    public ImportedContent load(URI sourceUrl, String userProvidedText) {
        try {
            return web.load(sourceUrl, userProvidedText);
        } catch (ExternalProviderException exception) {
            return ImportedContent.awaiting("블로그 내용을 자동으로 가져오지 못했습니다. 본문이나 장소 목록을 붙여 넣어 계속할 수 있습니다.");
        }
    }
}
