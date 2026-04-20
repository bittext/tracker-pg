package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementTaskCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementTaskCategoryRepository extends JpaRepository<ManagementTaskCategory, Long> {

    List<ManagementTaskCategory> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);
}
