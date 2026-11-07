package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.ManagementMailAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ManagementMailAccountRepository extends JpaRepository<ManagementMailAccount, Long> {

    List<ManagementMailAccount> findByOwnerUserIdOrderByProviderAscEmailAddressAsc(long ownerUserId);

    List<ManagementMailAccount> findByOwnerUserIdAndEnabledTrueOrderByProviderAscEmailAddressAsc(long ownerUserId);

    @Query("select distinct a.ownerUserId from ManagementMailAccount a where a.enabled = true")
    List<Long> findEnabledOwnerIds();
}
