package com.svp.tracker.fitness.service;

import com.svp.tracker.auth.security.CurrentUserService;
import com.svp.tracker.fitness.domain.AppleHealthImport;
import com.svp.tracker.fitness.domain.AppleHealthWeight;
import com.svp.tracker.fitness.domain.AppleHealthWorkout;
import com.svp.tracker.fitness.dto.AppleHealthImportResultDto;
import com.svp.tracker.fitness.dto.AppleHealthSummaryDto;
import com.svp.tracker.fitness.dto.AppleHealthWeightDto;
import com.svp.tracker.fitness.dto.AppleHealthWorkoutDto;
import com.svp.tracker.fitness.exception.NotFoundException;
import com.svp.tracker.fitness.repository.AppleHealthImportRepository;
import com.svp.tracker.fitness.repository.AppleHealthWeightRepository;
import com.svp.tracker.fitness.repository.AppleHealthWorkoutRepository;
import com.svp.tracker.fitness.service.AppleHealthExportParser.Parsed;
import com.svp.tracker.fitness.service.AppleHealthExportParser.ParsedWeight;
import com.svp.tracker.fitness.service.AppleHealthExportParser.ParsedWorkout;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppleHealthService {

    private final CurrentUserService currentUser;
    private final AppleHealthWorkoutRepository workoutRepository;
    private final AppleHealthWeightRepository weightRepository;
    private final AppleHealthImportRepository importRepository;

    public AppleHealthImportResultDto importExport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an Apple Health export.zip or export.xml.");
        }
        String fileName = file.getOriginalFilename() == null ? "export.xml" : file.getOriginalFilename();
        Parsed parsed;
        try {
            parsed = AppleHealthExportParser.parse(file.getInputStream(), fileName);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read that Apple Health export.");
        }
        return store(currentUser.requireUserId(), fileName, parsed);
    }

    @Transactional
    public AppleHealthImportResultDto store(long owner, String fileName, Parsed parsed) {
        Set<String> knownWorkouts = new HashSet<>(workoutRepository.findFingerprintsByOwnerUserId(owner));
        Set<String> knownWeights = new HashSet<>(weightRepository.findFingerprintsByOwnerUserId(owner));
        int workoutsAdded = 0;
        int workoutsSkipped = 0;
        int weightsAdded = 0;
        int weightsSkipped = 0;
        List<AppleHealthWorkout> workoutBatch = new ArrayList<>();
        for (ParsedWorkout row : parsed.workouts()) {
            if (!knownWorkouts.add(row.fingerprint())) {
                workoutsSkipped++;
                continue;
            }
            AppleHealthWorkout entity = new AppleHealthWorkout();
            entity.setOwnerUserId(owner);
            entity.setFingerprint(row.fingerprint());
            entity.setActivity(row.activity());
            entity.setSourceName(row.sourceName());
            entity.setStartedAt(row.startedAt());
            entity.setEndedAt(row.endedAt());
            entity.setDay(row.day());
            entity.setDurationMinutes(row.durationMinutes());
            entity.setDistanceMiles(row.distanceMiles());
            entity.setEnergyKcal(row.energyKcal());
            workoutBatch.add(entity);
            workoutsAdded++;
            if (workoutBatch.size() == 300) {
                workoutRepository.saveAll(workoutBatch);
                workoutBatch.clear();
            }
        }
        if (!workoutBatch.isEmpty()) {
            workoutRepository.saveAll(workoutBatch);
        }
        List<AppleHealthWeight> weightBatch = new ArrayList<>();
        for (ParsedWeight row : parsed.weights()) {
            if (!knownWeights.add(row.fingerprint())) {
                weightsSkipped++;
                continue;
            }
            AppleHealthWeight entity = new AppleHealthWeight();
            entity.setOwnerUserId(owner);
            entity.setFingerprint(row.fingerprint());
            entity.setLoggedOn(row.loggedOn());
            entity.setMeasuredAt(row.measuredAt());
            entity.setWeightLb(row.weightLb());
            entity.setSourceName(row.sourceName());
            weightBatch.add(entity);
            weightsAdded++;
            if (weightBatch.size() == 300) {
                weightRepository.saveAll(weightBatch);
                weightBatch.clear();
            }
        }
        if (!weightBatch.isEmpty()) {
            weightRepository.saveAll(weightBatch);
        }
        Instant importedAt = Instant.now();
        AppleHealthImport record = new AppleHealthImport();
        record.setOwnerUserId(owner);
        record.setFileName(clip(fileName));
        record.setImportedAt(importedAt);
        record.setWorkoutsAdded(workoutsAdded);
        record.setWeightsAdded(weightsAdded);
        importRepository.save(record);
        return new AppleHealthImportResultDto(
                record.getFileName(),
                importedAt,
                workoutsAdded,
                weightsAdded,
                workoutsSkipped,
                weightsSkipped,
                parsed.truncated());
    }

    @Transactional(readOnly = true)
    public AppleHealthSummaryDto summary() {
        long owner = currentUser.requireUserId();
        AppleHealthImport latest = importRepository.findFirstByOwnerUserIdOrderByImportedAtDesc(owner).orElse(null);
        return new AppleHealthSummaryDto(
                latest == null ? null : latest.getFileName(),
                latest == null ? null : latest.getImportedAt(),
                workoutRepository.countByOwnerUserId(owner),
                weightRepository.countByOwnerUserId(owner));
    }

    @Transactional(readOnly = true)
    public List<AppleHealthWorkoutDto> workouts(LocalDate from, LocalDate to) {
        return workoutRepository
                .findByOwnerUserIdAndDayBetweenOrderByStartedAtDesc(currentUser.requireUserId(), from, to)
                .stream()
                .map(row -> new AppleHealthWorkoutDto(
                        row.getId(),
                        row.getActivity(),
                        row.getSourceName(),
                        row.getStartedAt(),
                        row.getEndedAt(),
                        row.getDay(),
                        row.getDurationMinutes(),
                        row.getDistanceMiles(),
                        row.getEnergyKcal()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppleHealthWeightDto> weights(LocalDate from, LocalDate to) {
        return weightRepository
                .findByOwnerUserIdAndLoggedOnBetweenOrderByMeasuredAtDesc(currentUser.requireUserId(), from, to)
                .stream()
                .map(row -> new AppleHealthWeightDto(
                        row.getId(), row.getLoggedOn(), row.getMeasuredAt(), row.getWeightLb(), row.getSourceName()))
                .toList();
    }

    @Transactional
    public void clear() {
        long owner = currentUser.requireUserId();
        workoutRepository.deleteByOwnerUserId(owner);
        weightRepository.deleteByOwnerUserId(owner);
        importRepository.deleteByOwnerUserId(owner);
    }

    @Transactional
    public void deleteWeight(long id) {
        AppleHealthWeight row = weightRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Weight reading not found"));
        if (!row.getOwnerUserId().equals(currentUser.requireUserId())) {
            throw new NotFoundException("Weight reading not found");
        }
        weightRepository.delete(row);
    }

    @Transactional
    public void deleteWorkout(long id) {
        AppleHealthWorkout row = workoutRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Workout not found"));
        if (!row.getOwnerUserId().equals(currentUser.requireUserId())) {
            throw new NotFoundException("Workout not found");
        }
        workoutRepository.delete(row);
    }

    private static String clip(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "export.xml" : fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
}
