package com.svp.tracker.member.repository;

import com.svp.tracker.member.domain.MemberProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {

    Optional<MemberProfile> findByUserId(long userId);
}
