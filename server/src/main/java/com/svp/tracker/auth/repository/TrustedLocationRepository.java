package com.svp.tracker.auth.repository;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.TrustedLocation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedLocationRepository extends JpaRepository<TrustedLocation, Long> {

    Optional<TrustedLocation> findByUserAndLocationHash(AppUser user, String locationHash);
}
