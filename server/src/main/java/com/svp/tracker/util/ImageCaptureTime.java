package com.svp.tracker.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolve when an image was captured from embedded metadata (EXIF / PNG / XMP), filename cues, or
 * the browser file last-modified time — not the upload instant.
 *
 * <p>EXIF {@code DateTimeOriginal} is a <em>local wall clock</em> with no zone. We honor
 * OffsetTimeOriginal when present; otherwise interpret as America/Chicago so Lightsail (UTC) does
 * not shift evening photos to the afternoon.
 */
@Slf4j
public final class ImageCaptureTime {

    private static final ZoneId CENTRAL = ZoneId.of("America/Chicago");

    private static final DateTimeFormatter EXIF_LOCAL =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final DateTimeFormatter EXIF_LOCAL_FRAC =
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss.SSS");

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

    private ImageCaptureTime() {}

    public static Instant resolve(String originalFilename, String contentType, byte[] bytes) {
        return resolve(originalFilename, contentType, bytes, null);
    }

    /**
     * @param clientLastModifiedMs browser {@code File.lastModified} (epoch millis); used when the
     *     file has no usable embedded capture time (common for some PNGs). Matches Finder “Created”
     *     for many Mac photo/screenshot exports.
     */
    public static Instant resolve(
            String originalFilename, String contentType, byte[] bytes, Long clientLastModifiedMs) {
        Instant fromMeta = fromImageMetadata(contentType, originalFilename, bytes);
        if (fromMeta != null) {
            return fromMeta;
        }
        Instant fromName = fromFilename(originalFilename);
        if (fromName != null) {
            return fromName;
        }
        if (clientLastModifiedMs != null && clientLastModifiedMs > 0) {
            return Instant.ofEpochMilli(clientLastModifiedMs);
        }
        return null;
    }

    public static Instant fromFilename(String originalFilename) {
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
            return toCentralInstant(
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
        return toCentralInstant(y, m, d, hour, minute, second);
    }

    private static Instant toCentralInstant(int y, int m, int d, int hour, int minute, int second) {
        try {
            LocalDateTime ldt = LocalDateTime.of(LocalDate.of(y, m, d), LocalTime.of(hour, minute, second));
            return ldt.atZone(CENTRAL).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static Instant fromImageMetadata(String contentType, String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (!looksLikeImage(contentType, filename, bytes)) {
            return null;
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            Instant exif = fromExifDirectories(metadata);
            if (exif != null) {
                return exif;
            }
            Instant png = fromPng(metadata);
            if (png != null) {
                return png;
            }
            Instant xmp = fromXmp(metadata);
            if (xmp != null) {
                return xmp;
            }
        } catch (Exception e) {
            log.debug("Image capture metadata unavailable: {}", e.toString());
        }
        return null;
    }

    private static boolean looksLikeImage(String contentType, String filename, byte[] bytes) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/") || ct.isBlank() || ct.contains("octet-stream")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.matches(".*\\.(jpe?g|png|gif|webp|heic|heif|tiff?|bmp)$")) {
            return true;
        }
        // JPEG / PNG magic
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return true;
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return true;
        }
        return false;
    }

    private static Instant fromExifDirectories(Metadata metadata) {
        ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (sub != null) {
            Instant original = readExifLocalDateTime(
                    sub,
                    ExifDirectoryBase.TAG_DATETIME_ORIGINAL,
                    ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL);
            if (original != null) {
                return original;
            }
            Instant digitized = readExifLocalDateTime(
                    sub,
                    ExifDirectoryBase.TAG_DATETIME_DIGITIZED,
                    ExifDirectoryBase.TAG_TIME_ZONE_DIGITIZED);
            if (digitized != null) {
                return digitized;
            }
        }
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            Instant modified = readExifLocalDateTime(
                    ifd0, ExifDirectoryBase.TAG_DATETIME, ExifDirectoryBase.TAG_TIME_ZONE);
            if (modified != null) {
                return modified;
            }
        }
        return null;
    }

    /**
     * Parse EXIF date tags as wall-clock local time. Do <em>not</em> use {@link Directory#getDate(int)}
     * then {@link Date#toInstant()} — that applies the JVM default zone (UTC on Lightsail) and shifts
     * Central-evening captures by several hours.
     */
    private static Instant readExifLocalDateTime(Directory dir, int dateTag, int offsetTag) {
        String raw = dir.getString(dateTag);
        Instant parsed = parseExifDateTimeString(raw, dir.getString(offsetTag));
        if (parsed != null) {
            return parsed;
        }
        // Rare: library already decoded to Date — reinterpret calendar fields in Central.
        try {
            Date date = dir.getDate(dateTag);
            if (date != null) {
                LocalDateTime ldt =
                        LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
                String offset = dir.getString(offsetTag);
                if (offset != null && !offset.isBlank()) {
                    try {
                        return ldt.atOffset(ZoneOffset.of(offset.trim())).toInstant();
                    } catch (Exception ignored) {
                        // fall through to Central
                    }
                }
                return ldt.atZone(CENTRAL).toInstant();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    static Instant parseExifDateTimeString(String raw, String offsetRaw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().replace('/', ':');
        // Normalize "2026-07-27 21:13:00" → EXIF colon form
        if (cleaned.length() >= 19 && cleaned.charAt(4) == '-') {
            cleaned = cleaned.substring(0, 10).replace('-', ':') + cleaned.substring(10);
        }
        LocalDateTime ldt;
        try {
            if (cleaned.length() >= 23 && cleaned.charAt(19) == '.') {
                ldt = LocalDateTime.parse(cleaned.substring(0, 23), EXIF_LOCAL_FRAC);
            } else if (cleaned.length() >= 19) {
                ldt = LocalDateTime.parse(cleaned.substring(0, 19), EXIF_LOCAL);
            } else {
                return null;
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        if (offsetRaw != null && !offsetRaw.isBlank()) {
            try {
                return ldt.atOffset(ZoneOffset.of(offsetRaw.trim())).toInstant();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return ldt.atZone(CENTRAL).toInstant();
    }

    private static Instant fromPng(Metadata metadata) {
        for (PngDirectory dir : metadata.getDirectoriesOfType(PngDirectory.class)) {
            Object textual = dir.getObject(PngDirectory.TAG_TEXTUAL_DATA);
            if (textual instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                    if (key.contains("creation") || key.equals("create") || key.contains("datetime")) {
                        Instant parsed = parseFlexibleDateTime(String.valueOf(e.getValue()));
                        if (parsed != null) {
                            return parsed;
                        }
                    }
                }
            }
            Instant lastMod = parseFlexibleDateTime(dir.getString(PngDirectory.TAG_LAST_MODIFICATION_TIME));
            if (lastMod != null) {
                return lastMod;
            }
            try {
                Date d = dir.getDate(PngDirectory.TAG_LAST_MODIFICATION_TIME);
                if (d != null) {
                    LocalDateTime ldt = LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
                    return ldt.atZone(CENTRAL).toInstant();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return null;
    }

    private static Instant fromXmp(Metadata metadata) {
        XmpDirectory xmp = metadata.getFirstDirectoryOfType(XmpDirectory.class);
        if (xmp == null) {
            return null;
        }
        Map<String, String> props = xmp.getXmpProperties();
        if (props == null || props.isEmpty()) {
            return null;
        }
        String[] preferred = {
            "exif:DateTimeOriginal",
            "xmp:CreateDate",
            "photoshop:DateCreated",
            "exif:DateTimeDigitized",
            "xmp:ModifyDate"
        };
        for (String key : preferred) {
            Instant direct = parseFlexibleDateTime(props.get(key));
            if (direct != null) {
                return direct;
            }
        }
        for (Map.Entry<String, String> e : props.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
            if (k.contains("datetimeoriginal") || k.contains("createdate") || k.contains("datecreated")) {
                Instant parsed = parseFlexibleDateTime(e.getValue());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    /** PNG / XMP style timestamps, including ISO-8601 with offset. */
    static Instant parseFlexibleDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) {
            // continue
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
            // continue
        }
        Instant exif = parseExifDateTimeString(s, null);
        if (exif != null) {
            return exif;
        }
        // "2026-07-27T21:13:00" without zone → Central
        try {
            LocalDateTime ldt = LocalDateTime.parse(s);
            return ldt.atZone(CENTRAL).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
