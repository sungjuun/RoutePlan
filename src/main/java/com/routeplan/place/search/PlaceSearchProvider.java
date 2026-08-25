package com.routeplan.place.search;

import java.util.List;

public interface PlaceSearchProvider {

    List<PlaceSearchResult> search(PlaceSearchQuery query);
}
