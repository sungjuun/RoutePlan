package com.routeplan.place.search;

import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.place",
        name = "provider",
        havingValue = "DISABLED",
        matchIfMissing = true
)
public class DisabledPlaceSearchProvider implements PlaceSearchProvider {

    @Override
    public List<PlaceSearchResult> search(PlaceSearchQuery query) {
        throw new ExternalProviderException(
                ExternalProviderFailure.NOT_CONFIGURED,
                "장소 검색 Provider가 설정되지 않았습니다."
        );
    }
}
