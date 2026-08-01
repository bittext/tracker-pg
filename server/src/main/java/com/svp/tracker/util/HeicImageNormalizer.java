package com.svp.tracker.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Apple HEIC/HEIF uploads are accepted, but most browsers cannot decode them. Convert to JPEG for
 * storage and for inline display so thumbnails / markdown embeds work in Chrome and Firefox.
 */
@Slf4j
public final class HeicImageNormalizer {

    private HeicImageNormalizer() {}

    public record NormalizedImage(byte[] bytes, String filename, String contentType) {}

    /**
     * If the payload is HEIC/HEIF, return JPEG bytes with {@code image/jpeg} and a {@code .jpg}
     * filename. Otherwise return the inputs unchanged.
     */
    public static NormalizedImage normalize(String filename, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new NormalizedImage(bytes == null ? new byte[0] : bytes, filename, contentType);
        }
        if (!isHeic(filename, contentType, bytes)) {
            return new NormalizedImage(bytes, filename, contentType);
        }
        byte[] jpeg = convertToJpeg(bytes, filename);
        String outName = replaceExtension(filename == null || filename.isBlank() ? "image.heic" : filename, ".jpg");
        return new NormalizedImage(jpeg, outName, "image/jpeg");
    }

    public static boolean isHeic(String filename, String contentType, byte[] bytes) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (ct.contains("heic") || ct.contains("heif")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".heic") || name.endsWith(".heif") || name.endsWith(".hif")) {
            return true;
        }
        return looksLikeHeicContainer(bytes);
    }

    /** ISO BMFF: bytes[4..7] == "ftyp" and brand is a HEIF brand. */
    private static boolean looksLikeHeicContainer(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return switch (brand) {
            case "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heim", "heis", "hevm", "hevs" -> true;
            default -> false;
        };
    }

    private static byte[] convertToJpeg(byte[] heicBytes, String filename) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("tracker-heic-");
            String leaf = safeLeaf(filename);
            Path in = dir.resolve(leaf.endsWith(".heic") || leaf.endsWith(".heif") || leaf.endsWith(".hif")
                    ? leaf
                    : leaf + ".heic");
            Path out = dir.resolve("out.jpg");
            Files.write(in, heicBytes);

            Exception last = null;
            for (String[] cmd : conversionCommands(in, out)) {
                try {
                    run(cmd, 60);
                    if (Files.isRegularFile(out) && Files.size(out) > 0) {
                        return Files.readAllBytes(out);
                    }
                } catch (Exception e) {
                    last = e;
                    log.debug("HEIC convert attempt failed ({}): {}", cmd[0], e.toString());
                    try {
                        Files.deleteIfExists(out);
                    } catch (IOException ignored) {
                        // best-effort
                    }
                }
            }
            String hint = last == null ? "no converter succeeded" : last.getMessage();
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not convert HEIC/HEIF to JPEG for display. Install libheif-examples (heif-convert) or ImageMagick. "
                            + hint);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Could not convert HEIC/HEIF to JPEG: " + e.getMessage());
        } finally {
            deleteTreeQuietly(dir);
        }
    }

    private static String[][] conversionCommands(Path in, Path out) {
        String inPath = in.toAbsolutePath().toString();
        String outPath = out.toAbsolutePath().toString();
        return new String[][] {
            {"heif-convert", inPath, outPath},
            {"magick", inPath, outPath},
            {"convert", inPath, outPath},
            {"sips", "-s", "format", "jpeg", inPath, "--out", outPath},
            {"ffmpeg", "-y", "-i", inPath, "-frames:v", "1", outPath},
        };
    }

    private static void run(String[] command, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException(command[0] + " timed out");
        }
        if (process.exitValue() != 0) {
            String hint = output == null ? "" : output.trim();
            if (hint.length() > 200) {
                hint = hint.substring(hint.length() - 200);
            }
            throw new IOException(command[0] + " exit " + process.exitValue() + (hint.isEmpty() ? "" : ": " + hint));
        }
    }

    private static String replaceExtension(String filename, String newExt) {
        String leaf = safeLeaf(filename);
        int dot = leaf.lastIndexOf('.');
        String base = dot > 0 ? leaf.substring(0, dot) : leaf;
        if (base.isBlank()) {
            base = "image";
        }
        return base + newExt;
    }

    private static String safeLeaf(String filename) {
        if (filename == null || filename.isBlank()) {
            return "image.heic";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String leaf = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        leaf = leaf.replaceAll("[^a-zA-Z0-9._-]", "_");
        return leaf.isBlank() ? "image.heic" : leaf;
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }
}
