package com.svp.tracker.management.repo;

import com.svp.tracker.management.domain.ManagementDayOneTagDef;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementDayOneTagDefRepository extends JpaRepository<ManagementDayOneTagDef, Long> {

    Optional<ManagementDayOneTagDef> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
