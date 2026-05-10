package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.BankingImportFile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankingImportFileRepository extends JpaRepository<BankingImportFile, Long> {

    boolean existsByOwnerUserIdAndSha256Hex(long ownerUserId, String sha256Hex);

    boolean existsByOwnerUserIdAndInstitutionIdAndSha256Hex(long ownerUserId, long institutionId, String sha256Hex);

    Optional<BankingImportFile> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<BankingImportFile> findByOwnerUserIdOrderByCreatedAtDesc(long ownerUserId);

    @Query(
            """
            SELECT f FROM BankingImportFile f
            JOIN FETCH f.institution inst
            LEFT JOIN FETCH inst.institutionType
            WHERE f.ownerUserId = :ownerId
            AND f.createdAt >= :fromInclusive
            AND f.createdAt < :toExclusive
            AND (:institutionId IS NULL OR f.institution.id = :institutionId)
            AND (:institutionTypeId IS NULL OR (inst.institutionType IS NOT NULL
                AND inst.institutionType.id = :institutionTypeId))
            ORDER BY f.createdAt DESC
            """)
    List<BankingImportFile> listUploadedInRange(
            @Param("ownerId") long ownerId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("institutionId") Long institutionId,
            @Param("institutionTypeId") Long institutionTypeId);
}
