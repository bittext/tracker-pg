package com.svp.tracker.member.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.svp.tracker.member.dto.UsPostalValidationResponseDto;
import com.svp.tracker.member.dto.UsPostalValidationResponseDto.UsPostalPlaceDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class UsPostalValidationService {

    private final RestClient zippo;

    public UsPostalValidationService(ClientHttpRequestFactory trackerOutboundHttpRequestFactory) {
        this.zippo = RestClient.builder()
                .requestFactory(trackerOutboundHttpRequestFactory)
                .baseUrl("https://api.zippopotam.us")
                .build();
    }

    /**
     * Looks up a US ZIP code via Zippopotam (public HTTP API). Returns empty places when the code is unknown or the
     * service is unreachable.
     */
    public UsPostalValidationResponseDto lookupUsZip(String postalCode) {
        String zip = normalizeUsZip(postalCode);
        if (zip == null) {
            return new UsPostalValidationResponseDto(
                    postalCode != null ? postalCode.trim() : "",
                    List.of(),
                    "none",
                    "Enter a 5-digit US ZIP code.");
        }
        try {
            ZippoResponse body = zippo.get().uri("/us/{zip}", zip).retrieve().body(ZippoResponse.class);
            if (body == null || body.places == null || body.places.isEmpty()) {
                return new UsPostalValidationResponseDto(zip, List.of(), "zippopotam", "No places returned for this ZIP.");
            }
            List<UsPostalPlaceDto> places = new ArrayList<>();
            for (ZippoPlace p : body.places) {
                if (p.placeName == null) {
                    continue;
                }
                places.add(new UsPostalPlaceDto(
                        p.placeName,
                        p.stateAbbreviation != null ? p.stateAbbreviation : "",
                        p.state != null ? p.state : ""));
            }
            return new UsPostalValidationResponseDto(zip, places, "zippopotam", null);
        } catch (RestClientException ex) {
            return new UsPostalValidationResponseDto(zip, List.of(), "error", "Could not reach ZIP lookup service.");
        }
    }

    private static String normalizeUsZip(String raw) {
        if (raw == null) {
            return null;
        }
        String d = raw.replaceAll("\\D", "");
        if (d.length() >= 5) {
            return d.substring(0, 5);
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ZippoResponse {
        @JsonProperty("post code")
        String postCode;

        List<ZippoPlace> places;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ZippoPlace {
        @JsonProperty("place name")
        String placeName;

        @JsonProperty("state abbreviation")
        String stateAbbreviation;

        String state;
    }
}
