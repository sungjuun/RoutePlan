package com.routeplan.contentimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.contentimport.application.ContentSourceDetector;
import com.routeplan.contentimport.application.RuleBasedContentPlaceExtractor;
import com.routeplan.contentimport.domain.ContentSourceType;
import com.routeplan.contentimport.integration.WebUrlSecurityPolicy;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ContentImportParsingTest {

    @Test
    void detectsSupportedSocialHostsWithoutAcceptingLookalikeDomains() {
        ContentSourceDetector detector = new ContentSourceDetector();

        assertThat(detector.detect("https://www.instagram.com/p/abc").sourceType())
                .isEqualTo(ContentSourceType.INSTAGRAM);
        assertThat(detector.detect("https://m.youtube.com/watch?v=1").sourceType())
                .isEqualTo(ContentSourceType.YOUTUBE);
        assertThat(detector.detect("https://instagram.com.evil.example/p/abc").sourceType())
                .isEqualTo(ContentSourceType.GENERIC_WEB);
    }

    @Test
    void rejectsNonHttpAndPrivateNetworkUrls() {
        ContentSourceDetector detector = new ContentSourceDetector();
        WebUrlSecurityPolicy policy = new WebUrlSecurityPolicy();

        assertThatThrownBy(() -> detector.detect("file:///etc/passwd"))
                .isInstanceOfSatisfying(RoutePlanException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONTENT_IMPORT_UNSUPPORTED_URL));
        assertThatThrownBy(() -> policy.requirePublicHttpUrl(URI.create("http://127.0.0.1/admin")))
                .isInstanceOf(RoutePlanException.class);
        assertThatThrownBy(() -> policy.requirePublicHttpUrl(URI.create("http://169.254.169.254/latest/meta-data")))
                .isInstanceOf(RoutePlanException.class);
        assertThatThrownBy(() -> policy.requirePublicHttpUrl(URI.create("http://10.0.0.1:8080/")))
                .isInstanceOf(RoutePlanException.class);
    }

    @Test
    void extractsLineSeparatedAndHashtagPlaceNamesDeterministically() {
        RuleBasedContentPlaceExtractor extractor = new RuleBasedContentPlaceExtractor();

        assertThat(extractor.extract(1L, "서울 여행", """
                1. 경복궁
                2. 북촌한옥마을, 국립현대미술관 서울
                #경복궁 #서촌카페거리 #여행
                """))
                .contains("경복궁", "북촌한옥마을", "국립현대미술관 서울", "서촌카페거리")
                .doesNotHaveDuplicates();
    }
}
