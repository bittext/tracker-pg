package com.svp.tracker.finance.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Resolve when an image was captured (EXIF or filename), not when it was uploaded. */
@Slf4j
final class JournalImageCaptureTime {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

    /** SCR-20260724-225612… / SCR-20260724_225612… */
    private static final Pattern SCR =
            Pattern.compile("SCR[-_](\\d{8})[-_]?(\\d{6})?", Pattern.CASE_INSENSITIVE);
    /** IMG_20260724_225612 / 20260724_225612 */
    private static final Pattern IMG =
            Pattern.compile("(?:IMG[_-]?)?(\\d{8})[_-](\\d{6})", Pattern.CASE_INSENSITIVE);
    /** Screenshot 2026-07-24 at 10.56.00 PM / Screenshot 2026-07-24 at 22.56.00 */
    private static final Pattern MAC_SCREENSHOT = Pattern.compile(
            "Screenshot\\s+(\\d{4})-(\\d{2})-(\\d{2})\\s+at\\s+(\\d{1,2})[.:](\\d{2})[.:](\\d{2})(?:\\s*(AM|PM))?",
            Pattern.CASE_INSENSITIVE);

    private JournalImageCaptureTime() {}

    static Instant resolve(String originalFilename, String contentType, byte[] bytes) {
        Instant fromExif = fromExif(contentType, bytes);
        if (fromExif != null) {
            return fromExif;
        }
        Instant fromName = fromFilename(originalFilename);
        if (fromName != null) {
            return fromName;
        }
        return null;
    }

    static Instant fromFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String name = originalFilename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }

        Matcher mac = MAC_SCREENSHOT.matcher(name);
        if (mac.find()) {
            int hour = Integer.parseInt(mac.group(4));
            int minute = Integer.parseInt(mac.group(5));
            int second = Integer.parseInt(mac.group(6));
            String ampm = mac.group(7);
            if (ampm != null) {
                boolean pm = ampm.equalsIgnoreCase("PM");
                if (pm && hour < 12) {
                    hour += 12;
                } else if (!pm && hour == 12) {
                    hour = 0;
                }
            }
            return toInstant(
                    Integer.parseInt(mac.group(1)),
                    Integer.parseInt(mac.group(2)),
                    Integer.parseInt(mac.group(3)),
                    hour,
                    minute,
                    second);
        }

        Matcher scr = SCR.matcher(name);
        if (scr.find()) {
            return fromCompactDateTime(scr.group(1), scr.group(2));
        }

        Matcher img = IMG.matcher(name);
        if (img.find()) {
            return fromCompactDateTime(img.group(1), img.group(2));
        }
        return null;
    }

    private static Instant fromCompactDateTime(String yyyymmdd, String hhmmss) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) {
            return null;
        }
        int y = Integer.parseInt(yyyymmdd.substring(0, 4));
        int m = Integer.parseInt(yyyymmdd.substring(4, 6));
        int d = Integer.parseInt(yyyymmdd.substring(6, 8));
        if (hhmmss == null || hhmmss.length() != 6) {
            return LocalDate.of(y, m, d).atStartOfDay(CENTRAL).toInstant();
        }
        int hour = Integer.parseInt(hhmmss.substring(0, 2));
        int minute = Integer.parseInt(hhmmss.substring(2, 4));
        int second = Integer.parseInt(hhmmss.substring(4, 6));
        return toInstant(y, m, d, hour, minute, second);
    }

    private static Instant toInstant(int y, int m, int d, int hour, int minute, int second) {
        try {
            LocalDateTime ldt = LocalDateTime.of(LocalDate.of(y, m, d), LocalTime.of(hour, minute, second));
            return ldt.atZone(CENTRAL).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant fromExif(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!(ct.startsWith("image/") || ct.isBlank())) {
            return null;
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null) {
                Date original = sub.getDateOriginal();
                if (original != null) {
                    return original.toInstant();
                }
                Date digitized = sub.getDateDigitized();
                if (digitized != null) {
                    return digitized.toInstant();
                }
            }
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                Date modified = ifd0.getDate(ExifIFD0Directory.TAG_DATETIME);
                if (modified != null) {
                    return modified.toInstant();
                }
            }
        } catch (Exception e) {
            log.debug("EXIF capture time unavailable: {}", e.toString());
        }
        return null;
    }
}
