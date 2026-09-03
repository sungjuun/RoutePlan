package com.routeplan.contentimport.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "routeplan.ai", name = "provider", havingValue = "RULE_BASED", matchIfMissing = true)
public class RuleBasedContentPlaceExtractor implements ContentPlaceExtractor {
    private static final int MAX_PLACES = 20;
    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_-]{2,50})");
    private static final Set<String> BOILERPLATE = Set.of(
            "여행", "맛집", "카페", "추천", "일상", "광고", "협찬", "travel", "trip", "place"
    );

    @Override
    public List<String> extract(Long userId, String title, String text) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (text == null) return List.of();

        Matcher tags = HASHTAG.matcher(text);
        while (tags.find() && names.size() < MAX_PLACES) add(names, tags.group(1).replace('_', ' '));

        for (String segment : text.split("[\\n\\r,;|•]+")) {
            if (names.size() >= MAX_PLACES) break;
            String value = segment
                    .replaceAll("https?://\\S+", " ")
                    .replaceAll("^[\\s\\-–—*·•#\\d.)\\]]+", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (value.length() > 100 && value.contains(".")) {
                for (String sentence : value.split("[.!?]") ) {
                    if (names.size() >= MAX_PLACES) break;
                    add(names, sentence);
                }
            } else {
                add(names, value);
            }
        }
        return names.stream().limit(MAX_PLACES).toList();
    }

    private void add(LinkedHashSet<String> names, String value) {
        if (value == null) return;
        String normalized = value.trim().replaceAll("\\s+", " ");
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 100 || BOILERPLATE.contains(lowered)) return;
        if (normalized.matches("(?i)^(home|menu|login|sign up|copyright|privacy|terms).*$")) return;
        names.add(normalized);
    }
}
