package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingPlaidItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankingPlaidItemRepository extends JpaRepository<BankingPlaidItem, Long> {

    Optional<BankingPlaidItem> findByOwnerUserIdAndInstitution_Id(long ownerUserId, long institutionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from BankingPlaidItem b where b.ownerUserId = :ownerUserId and b.itemId = :itemId")
    void deleteAllByOwnerUserIdAndItemId(@Param("ownerUserId") long ownerUserId, @Param("itemId") String itemId);

    /** Clears any prior Plaid link on this institution (possibly a different Plaid item_id than the new exchange). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from BankingPlaidItem b where b.ownerUserId = :ownerUserId and b.institution.id = :institutionId")
    void deleteAllByOwnerUserIdAndInstitutionId(
            @Param("ownerUserId") long ownerUserId, @Param("institutionId") long institutionId);
}
