package com.svp.tracker.management.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.config.JournalProperties;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.journal.service.JournalBlobStore;
import com.svp.tracker.management.domain.TravelPlace;
import com.svp.tracker.management.domain.TravelPlaceStatus;
import com.svp.tracker.management.domain.TravelPlacePhoto;
import com.svp.tracker.management.domain.TravelTrip;
import com.svp.tracker.management.dto.TravelPlaceDto;
import com.svp.tracker.management.dto.TravelPlaceMapDto;
import com.svp.tracker.management.dto.TravelPlacePhotoDto;
import com.svp.tracker.management.dto.TravelPlacesReorderRequest;
import com.svp.tracker.management.dto.TravelPlaceWriteRequest;
import com.svp.tracker.management.dto.TravelTripDetailDto;
import com.svp.tracker.management.dto.TravelTripSummaryDto;
import com.svp.tracker.management.dto.TravelTripWriteRequest;
import com.svp.tracker.management.repository.TravelPlacePhotoRepository;
import com.svp.tracker.management.repository.TravelPlaceRepository;
import com.svp.tracker.management.repository.TravelTripRepository;
import com.svp.tracker.util.HeicImageNormalizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ManagementTravelService {

    private final TravelTripRepository tripRepository;
    private final TravelPlaceRepository placeRepository;
    private final TravelPlacePhotoRepository photoRepository;
    private final JournalBlobStore blobStore;
    private final JournalProperties journalProperties;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<TravelTripSummaryDto> listTrips() {
        long owner = currentUser.requireUserId();
        return tripRepository.listSummariesByOwner(owner);
    }

    @Transactional(readOnly = true)
    public TravelTripDetailDto getTrip(long tripId) {
        long owner = currentUser.requireUserId();
        TravelTrip trip = tripRepository
                .findByIdAndOwnerUserId(tripId, owner)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
        List<TravelPlace> places = placeRepository.findByTripIdWithPhotos(tripId);
        places.sort(Comparator.comparingInt(TravelPlace::getSortOrder).thenComparing(TravelPlace::getId));
        List<TravelPlaceDto> placeDtos = places.stream().map(p -> toPlaceDto(p, trip.getTitle())).toList();
        return toTripDetailDto(trip, placeDtos);
    }

    @Transactional(readOnly = true)
    public List<TravelPlaceMapDto> listPlacesForMap(LocalDate from, LocalDate to) {
        long owner = currentUser.requireUserId();
        List<TravelPlace> rows;
        if (from == null || to == null) {
            rows = placeRepository.findAllForOwnerWithTrip(owner);
        } else {
            if (to.isBefore(from)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
            }
            rows = placeRepository.findForOwnerInDateRangeWithTrip(
                    owner, from, to, TravelPlaceStatus.VISITED, TravelPlaceStatus.PLANNED);
        }
        return rows.stream().map(this::toMapDto).toList();
    }

    @Transactional
    public TravelTripDetailDto createTrip(TravelTripWriteRequest req) {
        long owner = currentUser.requireUserId();
        validateTripDates(req.startDate(), req.endDate());
        Instant now = Instant.now();
        TravelTrip t = new TravelTrip();
        t.setOwnerUserId(owner);
        t.setTitle(trimTitle(req.title()));
        t.setSummary(req.summary() == null ? "" : req.summary());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setStatus(req.status());
        t.setColorHex(normalizeColor(req.colorHex()));
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t = tripRepository.save(t);
        return getTrip(t.getId());
    }

    @Transactional
    public TravelTripDetailDto updateTrip(long tripId, TravelTripWriteRequest req) {
        long owner = currentUser.requireUserId();
        TravelTrip t = tripRepository
                .findByIdAndOwnerUserId(tripId, owner)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
        validateTripDates(req.startDate(), req.endDate());
        t.setTitle(trimTitle(req.title()));
        t.setSummary(req.summary() == null ? "" : req.summary());
        t.setStartDate(req.startDate());
        t.setEndDate(req.endDate());
        t.setStatus(req.status());
        t.setColorHex(normalizeColor(req.colorHex()));
        t.setUpdatedAt(Instant.now());
        tripRepository.save(t);
        return getTrip(tripId);
    }

    @Transactional
    public void deleteTrip(long tripId) {
        long owner = currentUser.requireUserId();
        TravelTrip t = tripRepository
                .findByIdAndOwnerUserId(tripId, owner)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
        List<TravelPlace> places = placeRepository.findByTripIdWithPhotos(tripId);
        for (TravelPlace p : places) {
            for (TravelPlacePhoto ph : new ArrayList<>(p.getPhotos())) {
                try {
                    blobStore.delete(ph.getStorageKey());
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }
        tripRepository.delete(t);
    }

    @Transactional
    public TravelTripDetailDto addPlace(long tripId, TravelPlaceWriteRequest req) {
        long owner = currentUser.requireUserId();
        TravelTrip trip = tripRepository
                .findByIdAndOwnerUserId(tripId, owner)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
        TravelPlace p = new TravelPlace();
        p.setTrip(trip);
        applyPlaceWrite(p, req);
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        trip.getPlaces().add(p);
        placeRepository.save(p);
        trip.setUpdatedAt(Instant.now());
        tripRepository.save(trip);
        return getTrip(tripId);
    }

    /**
     * Sets {@code sortOrder} on each place to match {@code orderedPlaceIds} (0-based index). The list must include
     * every place on the trip exactly once.
     */
    @Transactional
    public TravelTripDetailDto reorderPlaces(long tripId, TravelPlacesReorderRequest req) {
        long owner = currentUser.requireUserId();
        TravelTrip trip = tripRepository
                .findByIdAndOwnerUserId(tripId, owner)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));
        List<Long> ordered = req.orderedPlaceIds();
        List<TravelPlace> places = placeRepository.findByTripIdWithPhotos(tripId);
        if (places.isEmpty()) {
            if (!ordered.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip has no places to reorder");
            }
            return getTrip(tripId);
        }
        Set<Long> existing = places.stream().map(TravelPlace::getId).collect(Collectors.toSet());
        if (ordered.size() != existing.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "orderedPlaceIds must list every place on this trip exactly once");
        }
        if (!new HashSet<>(ordered).equals(existing)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "orderedPlaceIds must list every place on this trip exactly once");
        }
        Map<Long, TravelPlace> byId = places.stream().collect(Collectors.toMap(TravelPlace::getId, Function.identity()));
        Instant now = Instant.now();
        for (int i = 0; i < ordered.size(); i++) {
            TravelPlace p = byId.get(ordered.get(i));
            p.setSortOrder(i);
            p.setUpdatedAt(now);
        }
        placeRepository.saveAll(places);
        trip.setUpdatedAt(now);
        tripRepository.save(trip);
        return getTrip(tripId);
    }

    @Transactional
    public TravelTripDetailDto updatePlace(long placeId, TravelPlaceWriteRequest req) {
        long owner = currentUser.requireUserId();
        TravelPlace p = placeRepository
                .findByIdAndOwnerWithTrip(placeId, owner)
                .orElseThrow(() -> new NotFoundException("Place not found: " + placeId));
        applyPlaceWrite(p, req);
        p.setUpdatedAt(Instant.now());
        placeRepository.save(p);
        TravelTrip trip = p.getTrip();
        trip.setUpdatedAt(Instant.now());
        tripRepository.save(trip);
        return getTrip(trip.getId());
    }

    @Transactional
    public void deletePlace(long placeId) {
        long owner = currentUser.requireUserId();
        TravelPlace p = placeRepository
                .findByIdAndOwnerWithTrip(placeId, owner)
                .orElseThrow(() -> new NotFoundException("Place not found: " + placeId));
        TravelTrip trip = p.getTrip();
        deleteAllPhotosOnPlace(p);
        placeRepository.delete(p);
        trip.setUpdatedAt(Instant.now());
        tripRepository.save(trip);
    }

    @Transactional
    public TravelPlacePhotoDto addPhoto(long placeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File required");
        }
        long max = journalProperties.getMaxAttachmentBytes();
        if (file.getSize() > max) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds " + max + " bytes");
        }
        long owner = currentUser.requireUserId();
        TravelPlace p = placeRepository
                .findByIdAndOwnerWithTrip(placeId, owner)
                .orElseThrow(() -> new NotFoundException("Place not found: " + placeId));
        TravelTrip trip = p.getTrip();
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        var normalized = HeicImageNormalizer.normalize(filename, file.getContentType(), bytes);
        String key;
        try (var in = new ByteArrayInputStream(normalized.bytes())) {
            key = blobStore.put(trip.getOwnerUserId(), p.getId(), in, normalized.bytes().length);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        TravelPlacePhoto ph = new TravelPlacePhoto();
        ph.setPlace(p);
        ph.setStorageKey(key);
        ph.setOriginalFilename(normalized.filename());
        ph.setContentType(normalized.contentType());
        ph.setSizeBytes((long) normalized.bytes().length);
        ph.setCreatedAt(Instant.now());
        ph = photoRepository.save(ph);
        p.setUpdatedAt(Instant.now());
        trip.setUpdatedAt(Instant.now());
        tripRepository.save(trip);
        return toPhotoDto(ph);
    }

    @Transactional
    public void deletePhoto(long photoId) {
        TravelPlacePhoto ph = photoRepository
                .findByIdWithPlaceAndTrip(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        assertOwner(ph.getPlace().getTrip().getOwnerUserId());
        try {
            blobStore.delete(ph.getStorageKey());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        TravelTrip trip = ph.getPlace().getTrip();
        ph.getPlace().getPhotos().remove(ph);
        photoRepository.delete(ph);
        trip.setUpdatedAt(Instant.now());
        tripRepository.save(trip);
    }

    public record PhotoFile(String contentType, String filename, byte[] body) {}

    @Transactional(readOnly = true)
    public PhotoFile readPhotoFile(long photoId) {
        TravelPlacePhoto ph = photoRepository
                .findByIdWithPlaceAndTrip(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found: " + photoId));
        assertOwner(ph.getPlace().getTrip().getOwnerUserId());
        try {
            byte[] body = blobStore.readAllBytes(ph.getStorageKey());
            var normalized =
                    HeicImageNormalizer.normalize(ph.getOriginalFilename(), ph.getContentType(), body);
            return new PhotoFile(
                    normalized.contentType() != null
                            ? normalized.contentType()
                            : "application/octet-stream",
                    normalized.filename(),
                    normalized.bytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void deleteAllPhotosOnPlace(TravelPlace p) {
        for (TravelPlacePhoto ph : new ArrayList<>(p.getPhotos())) {
            try {
                blobStore.delete(ph.getStorageKey());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            photoRepository.delete(ph);
        }
        p.getPhotos().clear();
    }

    private void applyPlaceWrite(TravelPlace p, TravelPlaceWriteRequest req) {
        p.setName(trimPlaceName(req.name()));
        p.setLatitude(req.latitude());
        p.setLongitude(req.longitude());
        p.setAddress(req.address() == null || req.address().isBlank() ? null : req.address().trim());
        p.setPlaceStatus(req.placeStatus());
        p.setVisitDate(req.visitDate());
        p.setNotes(req.notes() == null ? "" : req.notes());
        p.setSortOrder(req.sortOrder());
    }

    private static void validateTripDates(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate");
        }
    }

    private void assertOwner(Long ownerUserId) {
        if (!Objects.equals(ownerUserId, currentUser.requireUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
    }

    private static String trimTitle(String title) {
        String t = title == null ? "" : title.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static String trimPlaceName(String name) {
        String t = name == null ? "" : name.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static String normalizeColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        String h = hex.trim();
        if (h.startsWith("#") && (h.length() == 7 || h.length() == 4)) {
            return h;
        }
        if (h.length() == 6 && !h.startsWith("#")) {
            return "#" + h;
        }
        return null;
    }

    private TravelPlaceMapDto toMapDto(TravelPlace p) {
        TravelTrip t = p.getTrip();
        return new TravelPlaceMapDto(
                p.getId(),
                t.getId(),
                t.getTitle(),
                t.getColorHex(),
                p.getName(),
                p.getLatitude(),
                p.getLongitude(),
                p.getPlaceStatus(),
                p.getVisitDate());
    }

    private TravelTripDetailDto toTripDetailDto(TravelTrip t, List<TravelPlaceDto> placeDtos) {
        return new TravelTripDetailDto(
                t.getId(),
                t.getOwnerUserId(),
                t.getTitle(),
                t.getSummary() == null ? "" : t.getSummary(),
                t.getStartDate(),
                t.getEndDate(),
                t.getStatus(),
                t.getColorHex(),
                placeDtos,
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private TravelPlaceDto toPlaceDto(TravelPlace p, String tripTitle) {
        List<TravelPlacePhoto> photos = new ArrayList<>(p.getPhotos());
        photos.sort(Comparator.comparing(TravelPlacePhoto::getId));
        List<TravelPlacePhotoDto> photoDtos = photos.stream().map(this::toPhotoDto).toList();
        return new TravelPlaceDto(
                p.getId(),
                p.getTrip().getId(),
                tripTitle,
                p.getName(),
                p.getLatitude(),
                p.getLongitude(),
                p.getAddress(),
                p.getPlaceStatus(),
                p.getVisitDate(),
                p.getNotes() == null ? "" : p.getNotes(),
                p.getSortOrder(),
                photoDtos,
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private TravelPlacePhotoDto toPhotoDto(TravelPlacePhoto ph) {
        return new TravelPlacePhotoDto(
                ph.getId(),
                ph.getOriginalFilename(),
                ph.getContentType(),
                ph.getSizeBytes(),
                "/api/management/travel/photos/" + ph.getId() + "/file");
    }
}
