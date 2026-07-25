package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.FinanceCompanyResearch;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceCompanyResearchRepository extends JpaRepository<FinanceCompanyResearch, Long> {

    List<FinanceCompanyResearch> findByOwnerUserIdOrderByUpdatedAtDesc(long ownerUserId);

    Optional<FinanceCompanyResearch> findByIdAndOwnerUserId(long id, long ownerUserId);

    Optional<FinanceCompanyResearch> findByOwnerUserIdAndSymbol(long ownerUserId, String symbol);

    List<FinanceCompanyResearch> findByOwnerUserIdAndDecisionStatusOrderByUpdatedAtDesc(
            long ownerUserId, String decisionStatus);

    List<FinanceCompanyResearch> findByOwnerUserIdAndNextEarningsDateBetweenOrderByNextEarningsDateAscSymbolAsc(
            long ownerUserId, LocalDate from, LocalDate to);

    List<FinanceCompanyResearch> findByOwnerUserIdAndSymbolIn(long ownerUserId, Collection<String> symbols);

    @Query(
            value =
                    """
                    SELECT DISTINCT r.*
                    FROM finance_company_research r
                    LEFT JOIN finance_company_research_note n
                      ON n.research_id = r.id AND n.owner_user_id = r.owner_user_id
                    WHERE r.owner_user_id = :ownerUserId
                      AND (
                        r.symbol ILIKE CONCAT('%', :q, '%')
                        OR r.company_name ILIKE CONCAT('%', :q, '%')
                        OR r.tags ILIKE CONCAT('%', :q, '%')
                        OR r.thesis ILIKE CONCAT('%', :q, '%')
                        OR COALESCE(n.note_text, '') ILIKE CONCAT('%', :q, '%')
                        OR COALESCE(n.tags, '') ILIKE CONCAT('%', :q, '%')
                      )
                    ORDER BY r.updated_at DESC
                    """,
            nativeQuery = true)
    List<FinanceCompanyResearch> search(@Param("ownerUserId") long ownerUserId, @Param("q") String q);
}
