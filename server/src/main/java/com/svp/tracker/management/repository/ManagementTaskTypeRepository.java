package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementTaskType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementTaskTypeRepository extends JpaRepository<ManagementTaskType, Long> {}
