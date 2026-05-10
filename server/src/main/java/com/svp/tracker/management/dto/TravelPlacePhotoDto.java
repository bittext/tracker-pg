package com.svp.tracker.management.dto;

public record TravelPlacePhotoDto(
        long id, String originalFilename, String contentType, long sizeBytes, String downloadPath) {}
