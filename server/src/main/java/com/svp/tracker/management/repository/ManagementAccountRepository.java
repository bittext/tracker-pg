package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementAccountRepository extends JpaRepository<ManagementAccount, Long> {

    List<ManagementAccount> findByOwnerUserIdOrderByFolderAscItemNameAscIdAsc(long ownerUserId);

    Optional<ManagementAccount> findByIdAndOwnerUserId(long id, long ownerUserId);

    long countByOwnerUserId(long ownerUserId);

    boolean existsByOwnerUserIdAndFolderIgnoreCaseAndItemNameIgnoreCase(
            long ownerUserId, String folder, String itemName);
}
