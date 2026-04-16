package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementTaskCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementTaskCategoryRepository extends JpaRepository<ManagementTaskCategory, Long> {}
