package com.svp.tracker.management.controller;

import com.svp.tracker.management.dto.TravelPlaceMapDto;
import com.svp.tracker.management.dto.TravelPlacePhotoDto;
import com.svp.tracker.management.dto.TravelPlaceWriteRequest;
import com.svp.tracker.management.dto.TravelTripDetailDto;
import com.svp.tracker.management.dto.TravelTripSummaryDto;
import com.svp.tracker.management.dto.TravelTripWriteRequest;
import com.svp.tracker.management.service.ManagementTravelService;
import com.svp.tracker.management.service.ManagementTravelService.PhotoFile;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/management/travel")
@RequiredArgsConstructor
public class ManagementTravelController {

    private final ManagementTravelService service;

    @GetMapping("/trips")
    public List<TravelTripSummaryDto> listTrips() {
        return service.listTrips();
    }

    @GetMapping("/trips/{id}")
    public TravelTripDetailDto getTrip(@PathVariable long id) {
        return service.getTrip(id);
    }

    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelTripDetailDto createTrip(@Valid @RequestBody TravelTripWriteRequest body) {
        return service.createTrip(body);
    }

    @PutMapping("/trips/{id}")
    public TravelTripDetailDto updateTrip(@PathVariable long id, @Valid @RequestBody TravelTripWriteRequest body) {
        return service.updateTrip(id, body);
    }

    @DeleteMapping("/trips/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrip(@PathVariable long id) {
        service.deleteTrip(id);
    }

    @GetMapping("/places")
    public List<TravelPlaceMapDto> listPlacesForMap(
            @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.listPlacesForMap(from, to);
    }

    @PostMapping("/trips/{tripId}/places")
    public TravelTripDetailDto addPlace(@PathVariable long tripId, @Valid @RequestBody TravelPlaceWriteRequest body) {
        return service.addPlace(tripId, body);
    }

    @PutMapping("/places/{placeId}")
    public TravelTripDetailDto updatePlace(
            @PathVariable long placeId, @Valid @RequestBody TravelPlaceWriteRequest body) {
        return service.updatePlace(placeId, body);
    }

    @DeleteMapping("/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlace(@PathVariable long placeId) {
        service.deletePlace(placeId);
    }

    @PostMapping("/places/{placeId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPlacePhotoDto uploadPhoto(@PathVariable long placeId, @RequestParam("file") MultipartFile file) {
        return service.addPhoto(placeId, file);
    }

    @DeleteMapping("/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable long photoId) {
        service.deletePhoto(photoId);
    }

    @GetMapping("/photos/{id}/file")
    public ResponseEntity<byte[]> downloadPhoto(
            @PathVariable("id") long photoId, @RequestParam(defaultValue = "inline") String disposition) {
        PhotoFile f = service.readPhotoFile(photoId);
        String mode = "attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(f.contentType()));
        h.setContentDisposition(
                ContentDisposition.builder(mode).filename(f.filename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(f.body(), h, HttpStatus.OK);
    }
}
