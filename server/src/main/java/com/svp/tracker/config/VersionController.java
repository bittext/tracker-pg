package com.svp.tracker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public release metadata (Maven {@code build-info} when packaged). */
@RestController
@RequestMapping("/api/version")
@RequiredArgsConstructor
public class VersionController {

    private final ObjectProvider<BuildProperties> buildProperties;

    @GetMapping
    public ResponseEntity<VersionResponse> version() {
        BuildProperties bp = buildProperties.getIfAvailable();
        if (bp != null) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)))
                    .body(new VersionResponse(
                            bp.getName(),
                            bp.getGroup(),
                            bp.getArtifact(),
                            bp.getVersion(),
                            bp.getTime() != null ? bp.getTime().toString() : null));
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(VersionResponse.development());
    }

    public record VersionResponse(String name, String group, String artifact, String version, String buildTime) {
        static VersionResponse development() {
            return new VersionResponse("tracker-pg-server", "com.svp", "tracker-pg-server", "development", null);
        }
    }
}
