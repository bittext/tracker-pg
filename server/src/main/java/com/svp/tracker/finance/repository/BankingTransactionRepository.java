package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingTransaction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankingTransactionRepository extends JpaRepository<BankingTransaction, Long> {

    void deleteByImportFile_Id(long importFileId);

    boolean existsByOwnerUserIdAndDedupeHash(long ownerUserId, String dedupeHash);

    @Query(
            """
            SELECT t FROM BankingTransaction t
            JOIN FETCH t.institution inst
            LEFT JOIN FETCH inst.institutionType
            JOIN FETCH t.importFile ifile
            WHERE t.ownerUserId = :ownerId
            AND t.txnDate >= :fromInclusive
            AND t.txnDate <= :toInclusive
            AND (:institutionId IS NULL OR t.institution.id = :institutionId)
            AND (:institutionTypeId IS NULL OR (inst.institutionType IS NOT NULL
                AND inst.institutionType.id = :institutionTypeId))
            ORDER BY t.txnDate DESC, t.id DESC
            """)
    List<BankingTransaction> listInRange(
            @Param("ownerId") long ownerId,
            @Param("fromInclusive") LocalDate fromInclusive,
            @Param("toInclusive") LocalDate toInclusive,
            @Param("institutionId") Long institutionId,
            @Param("institutionTypeId") Long institutionTypeId);

    @Query("SELECT t.dedupeHash FROM BankingTransaction t WHERE t.ownerUserId = :ownerId AND t.dedupeHash IN :hashes")
    List<String> findExistingDedupeHashes(@Param("ownerId") long ownerId, @Param("hashes") Collection<String> hashes);
}
