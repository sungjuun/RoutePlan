package com.routeplan.contentimport.application;

import com.routeplan.contentimport.domain.ContentSourceType;
import java.net.URI;

public interface ContentImporter {
    boolean supports(ContentSourceType sourceType);
    ImportedContent load(URI sourceUrl, String userProvidedText);

    record ImportedContent(String title, String text, boolean requiresUserInput, String warning) {
        public static ImportedContent content(String title, String text) {
            return new ImportedContent(title, text, false, null);
        }
        public static ImportedContent awaiting(String warning) {
            return new ImportedContent(null, null, true, warning);
        }
    }
}
