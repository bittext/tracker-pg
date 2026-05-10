package com.svp.tracker.management.repository;

import com.svp.tracker.management.domain.TravelPlacePhoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelPlacePhotoRepository extends JpaRepository<TravelPlacePhoto, Long> {

    @Query("SELECT DISTINCT ph FROM TravelPlacePhoto ph JOIN FETCH ph.place p JOIN FETCH p.trip t WHERE ph.id = :id")
    Optional<TravelPlacePhoto> findByIdWithPlaceAndTrip(@Param("id") long id);
}
