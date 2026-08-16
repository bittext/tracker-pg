package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodRhSupplementalCashFlow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodRhSupplementalCashFlowRepository
        extends JpaRepository<RobinhoodRhSupplementalCashFlow, Long> {

    List<RobinhoodRhSupplementalCashFlow> findByOwnerUserIdOrderByActivityDateAscIdAsc(long ownerUserId);

    List<RobinhoodRhSupplementalCashFlow>
            findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenAndFlowCategoryOrderByActivityDateAscIdAsc(
                    long ownerUserId,
                    String accountSuffix,
                    LocalDate fromInclusive,
                    LocalDate toInclusive,
                    String flowCategory);
}
