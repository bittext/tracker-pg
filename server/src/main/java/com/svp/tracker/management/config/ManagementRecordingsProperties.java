package com.svp.tracker.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Just Press Record library (local iCloud / disk path). Defaults to the Mac iCloud Documents folder when
 * unset so local API can browse immediately; set {@code enabled=false} or empty root on hosts without the folder.
 */
@ConfigurationProperties(prefix = "tracker.management.recordings")
public record ManagementRecordingsProperties(
        boolean enabled,
        /** Absolute path to Just Press Record Documents (date folders of .m4a files). */
        String rootPath) {

    public ManagementRecordingsProperties {
        if (rootPath == null) {
            rootPath = "";
        } else {
            rootPath = rootPath.trim();
        }
    }

    public boolean configured() {
        return enabled && rootPath != null && !rootPath.isBlank();
    }
}
