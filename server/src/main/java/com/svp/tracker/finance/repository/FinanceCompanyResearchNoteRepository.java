package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceCompanyResearchNote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceCompanyResearchNoteRepository extends JpaRepository<FinanceCompanyResearchNote, Long> {

    List<FinanceCompanyResearchNote> findByResearchIdAndOwnerUserIdOrderByCreatedAtDesc(
            long researchId, long ownerUserId);

    Optional<FinanceCompanyResearchNote> findByIdAndOwnerUserId(long id, long ownerUserId);

    long countByResearchIdAndOwnerUserId(long researchId, long ownerUserId);
}
