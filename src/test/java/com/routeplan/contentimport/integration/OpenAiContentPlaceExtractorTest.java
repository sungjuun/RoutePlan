package com.routeplan.contentimport.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.routeplan.ai.integration.openai.OpenAiHttpClient;
import com.routeplan.ai.integration.openai.OpenAiProperties;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import org.junit.jupiter.api.Test;

class OpenAiContentPlaceExtractorTest {

    @Test
    void fallsBackToRuleBasedExtractionWhenOpenAiIsRateLimited() {
        OpenAiHttpClient client = mock(OpenAiHttpClient.class);
        when(client.createResponse(any())).thenThrow(new ExternalProviderException(
                ExternalProviderFailure.RATE_LIMITED, "quota exceeded"));
        OpenAiContentPlaceExtractor extractor = new OpenAiContentPlaceExtractor(client, new OpenAiProperties());

        assertThat(extractor.extract(7L, "기타큐슈 맛집", "타마토야\n가라토시장\n만텐스시"))
                .containsExactly("타마토야", "가라토시장", "만텐스시");
    }
}
