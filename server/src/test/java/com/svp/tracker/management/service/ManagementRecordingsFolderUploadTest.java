package com.svp.tracker.management.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.svp.tracker.management.domain.ManagementRecordingCache;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagementRecordingsFolderUploadTest {

    @Test
    void matchesImageToSameStemInFolder() {
        ManagementRecordingCache clip = recording("2026-09-01/08-27-11.m4a");
        ManagementRecordingCache other = recording("2026-09-01/09-00-00.m4a");
        assertEquals(
                clip,
                ManagementRecordingsService.resolveImageTarget(
                        "2026-09-01/08-27-11.jpg", List.of(clip, other)));
    }

    @Test
    void attachesLoosePhotoToOnlyClipInFolder() {
        ManagementRecordingCache clip = recording("Work/call.m4a");
        assertEquals(
                clip, ManagementRecordingsService.resolveImageTarget("Work/slide.png", List.of(clip)));
    }

    @Test
    void ignoresPhotoWhenFolderHasNoRecording() {
        ManagementRecordingCache clip = recording("2026-09-01/clip.m4a");
        assertNull(ManagementRecordingsService.resolveImageTarget("2026-09-02/photo.jpg", List.of(clip)));
    }

    private static ManagementRecordingCache recording(String path) {
        ManagementRecordingCache row = new ManagementRecordingCache();
        row.setRelativePath(path);
        return row;
    }
}
