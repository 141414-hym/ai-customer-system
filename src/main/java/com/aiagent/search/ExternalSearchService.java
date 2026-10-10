package com.aiagent.search;

import java.util.List;

public interface ExternalSearchService {
    SearchResult search(String query, String locale);

    record SearchResult(String status, String summary, List<SearchItem> items) {
        public static SearchResult unavailable() { return new SearchResult("UNAVAILABLE", "暂未获得外部检索结果", List.of()); }
    }
    record SearchItem(String title, String url, String snippet) {}
}
