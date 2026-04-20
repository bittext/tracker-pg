package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementTask;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagementTaskRepository extends JpaRepository<ManagementTask, Long> {

    List<ManagementTask> findByDueDateBetweenOrderByDueDateAscIdAsc(LocalDate from, LocalDate to);

    List<ManagementTask> findByDueDateIsNullOrderByCreatedAtDesc();

    List<ManagementTask> findAllByOrderByDueDateAscIdAsc();

    List<ManagementTask> findByOwnerUserIdOrderByDueDateAscIdAsc(Long ownerUserId);

    List<ManagementTask> findByOwnerUserIdAndDueDateBetweenOrderByDueDateAscIdAsc(
            Long ownerUserId, LocalDate from, LocalDate to);

    List<ManagementTask> findByOwnerUserIdAndDueDateIsNullOrderByCreatedAtDesc(Long ownerUserId);

    @Modifying
    @Query("update ManagementTask t set t.category = null where t.category.id = :categoryId")
    void clearCategoryByCategoryId(@Param("categoryId") Long categoryId);

    @Modifying
    @Query("update ManagementTask t set t.taskType = null where t.taskType.id = :typeId")
    void clearTaskTypeByTypeId(@Param("typeId") Long typeId);
}
