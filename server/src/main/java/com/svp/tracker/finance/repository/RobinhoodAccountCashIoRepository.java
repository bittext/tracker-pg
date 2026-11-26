package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobinhoodAccountCashIoRepository extends JpaRepository<RobinhoodAccountCashIo, Long> {

    Optional<RobinhoodAccountCashIo> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<RobinhoodAccountCashIo> findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
            long ownerUserId, LocalDate fromInclusive, LocalDate toInclusive);

    List<RobinhoodAccountCashIo>
            findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                    long ownerUserId, String accountSuffix, LocalDate fromInclusive, LocalDate toInclusive);

    List<RobinhoodAccountCashIo> findByOwnerUserIdAndAccountSuffixOrderByActivityDateAscIdAsc(
            long ownerUserId, String accountSuffix);

    @Query("""
            select distinct r.counterparty from RobinhoodAccountCashIo r
            where r.ownerUserId = :ownerUserId and r.counterparty is not null
            order by r.counterparty
            """)
    List<String> findDistinctCounterparties(@Param("ownerUserId") long ownerUserId);
}
