package com.aiagent.search;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Adapter for an approved enterprise search gateway. No user identity is sent to search providers. */
@Service
public class HttpExternalSearchService implements ExternalSearchService {
    private final String endpoint;
    private final RestClient client;

    public HttpExternalSearchService(@Value("${assistant.search.endpoint:}") String endpoint) {
        this.endpoint = endpoint;
        this.client = RestClient.create();
    }

    @Override
    public SearchResult search(String query, String locale) {
        if (endpoint.isBlank()) return SearchResult.unavailable();
        try {
            SearchResult result = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .body(new SearchRequest(query, locale)).retrieve().body(SearchResult.class);
            return result == null ? SearchResult.unavailable() : result;
        } catch (RuntimeException ex) {
            return SearchResult.unavailable();
        }
    }

    private record SearchRequest(String query, String locale) {}
}
