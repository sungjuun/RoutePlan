package com.routeplan.contentimport.integration;

import com.routeplan.contentimport.application.ContentImporter;
import com.routeplan.contentimport.domain.ContentSourceType;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class InstagramContentImporter implements ContentImporter {
    @Override
    public boolean supports(ContentSourceType sourceType) {
        return sourceType == ContentSourceType.INSTAGRAM;
    }

    @Override
    public ImportedContent load(URI sourceUrl, String userProvidedText) {
        if (userProvidedText == null || userProvidedText.isBlank()) {
            return ImportedContent.awaiting(
                    "Instagram 게시물은 자동 크롤링하지 않습니다. 게시물의 캡션이나 장소 목록을 붙여 넣어 주세요."
            );
        }
        return ImportedContent.content("Instagram에서 가져온 장소", userProvidedText.trim());
    }
}
