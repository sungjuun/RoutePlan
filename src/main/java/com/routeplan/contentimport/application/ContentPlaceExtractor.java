package com.routeplan.contentimport.application;

import java.util.List;

public interface ContentPlaceExtractor {
    List<String> extract(Long userId, String title, String text);
}
