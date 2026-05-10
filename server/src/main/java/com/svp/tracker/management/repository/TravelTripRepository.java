package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.TravelTrip;
import com.svp.tracker.management.dto.TravelTripSummaryDto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelTripRepository extends JpaRepository<TravelTrip, Long> {

    @Query(
            "SELECT new com.svp.tracker.management.dto.TravelTripSummaryDto(t.id, t.title, t.startDate, t.endDate, t.status, t.colorHex, COUNT(p.id), t.createdAt, t.updatedAt) "
                    + "FROM TravelTrip t LEFT JOIN t.places p WHERE t.ownerUserId = :owner "
                    + "GROUP BY t.id, t.title, t.startDate, t.endDate, t.status, t.colorHex, t.createdAt, t.updatedAt "
                    + "ORDER BY t.startDate DESC")
    List<TravelTripSummaryDto> listSummariesByOwner(@Param("owner") long ownerUserId);

    List<TravelTrip> findByOwnerUserIdOrderByStartDateDesc(Long ownerUserId);

    Optional<TravelTrip> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Query("SELECT DISTINCT t FROM TravelTrip t LEFT JOIN FETCH t.places WHERE t.id = :id AND t.ownerUserId = :owner")
    Optional<TravelTrip> findByIdAndOwnerWithPlaces(@Param("id") long id, @Param("owner") long ownerUserId);
}
