package com.svp.tracker.fitness.repository;

import com.svp.tracker.fitness.domain.AppleHealthImport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppleHealthImportRepository extends JpaRepository<AppleHealthImport, Long> {

    Optional<AppleHealthImport> findFirstByOwnerUserIdOrderByImportedAtDesc(long ownerUserId);

    void deleteByOwnerUserId(long ownerUserId);
}
