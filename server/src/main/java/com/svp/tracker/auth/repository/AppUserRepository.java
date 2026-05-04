package com.svp.tracker.auth.repository;

import com.svp.tracker.auth.domain.AppUser;
import com.svp.tracker.auth.domain.AppUserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    List<AppUser> findByRole(AppUserRole role);

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, long id);

    boolean existsByMemberPublicId(long memberPublicId);

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM auth_users u
                        WHERE u.id <> :excludeUserId
                          AND btrim(u.email) <> ''
                          AND LOWER(btrim(u.email)) = LOWER(btrim(:email))
                    )
                    """,
            nativeQuery = true)
    boolean existsOtherAuthUserWithNormalizedEmail(@Param("excludeUserId") long excludeUserId, @Param("email") String email);

    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM auth_users u
                        WHERE btrim(u.email) <> ''
                          AND LOWER(btrim(u.email)) = LOWER(btrim(:email))
                    )
                    """,
            nativeQuery = true)
    boolean existsAnyAuthUserWithNormalizedEmail(@Param("email") String email);
}
