package com.svp.tracker.auth.repository;

import com.svp.tracker.auth.domain.MfaChallenge;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, String> {

    Optional<MfaChallenge> findByIdAndUsedAtIsNull(String id);

    long countByUser_IdAndCreatedAtAfter(Long userId, Instant since);
}
