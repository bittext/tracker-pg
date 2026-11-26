package com.svp.tracker.finance.repository;

import com.svp.tracker.finance.domain.RobinhoodAccountCashIo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobinhoodAccountCashIoRepository extends JpaRepository<RobinhoodAccountCashIo, Long> {

    Optional<RobinhoodAccountCashIo> findByIdAndOwnerUserId(long id, long ownerUserId);

    List<RobinhoodAccountCashIo> findByOwnerUserIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
            long ownerUserId, LocalDate fromInclusive, LocalDate toInclusive);

    List<RobinhoodAccountCashIo>
            findByOwnerUserIdAndAccountSuffixAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                    long ownerUserId, String accountSuffix, LocalDate fromInclusive, LocalDate toInclusive);

    List<RobinhoodAccountCashIo> findByOwnerUserIdAndAccountSuffixOrderByActivityDateAscIdAsc(
            long ownerUserId, String accountSuffix);
}
