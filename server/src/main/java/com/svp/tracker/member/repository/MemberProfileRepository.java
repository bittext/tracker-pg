package com.svp.tracker.member.repository;

import com.svp.tracker.member.domain.MemberProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {

    Optional<MemberProfile> findByUserId(long userId);

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM member_profiles p
                        WHERE p.user_id <> :userId
                          AND btrim(p.email) <> ''
                          AND LOWER(btrim(p.email)) = LOWER(btrim(:email))
                    )
                    """,
            nativeQuery = true)
    boolean existsOtherUserWithNormalizedEmail(@Param("userId") long userId, @Param("email") String email);

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM member_profiles p
                        WHERE btrim(p.email) <> ''
                          AND LOWER(btrim(p.email)) = LOWER(btrim(:email))
                    )
                    """,
            nativeQuery = true)
    boolean existsAnyMemberProfileWithNormalizedEmail(@Param("email") String email);
}
