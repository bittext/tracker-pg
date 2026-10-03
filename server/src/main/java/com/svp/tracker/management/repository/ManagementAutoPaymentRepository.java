package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementAutoPayment;
import com.svp.tracker.management.domain.ManagementDesk;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagementAutoPaymentRepository extends JpaRepository<ManagementAutoPayment, Long> {

    List<ManagementAutoPayment> findByOwnerUserIdAndDeskOrderByNameAscIdAsc(long ownerUserId, ManagementDesk desk);

    Optional<ManagementAutoPayment> findByIdAndOwnerUserId(long id, long ownerUserId);
}
