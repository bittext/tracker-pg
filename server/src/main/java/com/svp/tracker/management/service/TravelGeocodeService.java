package com.svp.tracker.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.svp.tracker.management.dto.TravelGeocodeResultDto;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forward geocoding for Management → Travel, using <a href="https://nominatim.org/">OpenStreetMap Nominatim</a>. Calls
 * go through this server with a proper {@code User-Agent} (required by the usage policy); do not call Nominatim
 * directly from the browser.
 */
@Service
public class TravelGeocodeService {

    private static final int MIN_QUERY_LEN = 3;
    private static final int MAX_QUERY_LEN = 300;

    /** Spring Boot 4 does not expose a {@code com.fasterxml.jackson.databind.ObjectMapper} bean; local mapper for Nominatim JSON only. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient nominatim;
    private final String userAgent;

    public TravelGeocodeService(
            ClientHttpRequestFactory trackerOutboundHttpRequestFactory,
            @Value(
                    "${tracker.management.travel.geocode-user-agent:tracker-pg/9.5.1 (github.com/bittext/tracker-pg; travel geocode)}")
                    String userAgent) {
        this.userAgent = userAgent;
        this.nominatim = RestClient.builder()
                .requestFactory(trackerOutboundHttpRequestFactory)
                .baseUrl("https://nominatim.openstreetmap.org")
                .build();
    }

    public TravelGeocodeResultDto geocode(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query is required");
        }
        String q = rawQuery.trim();
        if (q.length() < MIN_QUERY_LEN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Query must be at least " + MIN_QUERY_LEN + " characters");
        }
        if (q.length() > MAX_QUERY_LEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query is too long");
        }
        String body;
        try {
            body = nominatim
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", q)
                            .queryParam("format", "json")
                            .queryParam("limit", "1")
                            .queryParam("addressdetails", "1")
                            .build())
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Geocoding service unavailable", e);
        }
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from geocoding service");
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse geocoding response", e);
        }
        if (!root.isArray() || root.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No location found for that search");
        }
        JsonNode hit = root.get(0);
        Double lat = readDouble(hit, "lat");
        Double lon = readDouble(hit, "lon");
        if (lat == null || lon == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Geocoding result missing coordinates");
        }
        String displayName = textOrEmpty(hit, "display_name");
        JsonNode addr = hit.get("address");
        String country = addr != null && addr.isObject() ? textOrEmpty(addr, "country") : "";
        String region = addr != null && addr.isObject() ? pickRegion(addr) : "";
        String locality = addr != null && addr.isObject() ? pickLocality(addr) : "";
        return new TravelGeocodeResultDto(lat, lon, displayName, country, region, locality);
    }

    private static Double readDouble(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || !n.isTextual()) {
            return null;
        }
        try {
            return Double.parseDouble(n.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && n.isTextual() ? n.asText() : "";
    }

    private static String pickLocality(JsonNode addr) {
        for (String key :
                new String[] {"city", "town", "village", "hamlet", "municipality", "suburb", "city_district"}) {
            String v = textOrEmpty(addr, key);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return textOrEmpty(addr, "county");
    }

    private static String pickRegion(JsonNode addr) {
        for (String key : new String[] {"state", "region", "province"}) {
            String v = textOrEmpty(addr, key);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }
}
