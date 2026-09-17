package com.rootrecord.minecraft.rootwebstat;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads Paper {@code logs/latest.log} with line/byte caps. */
public final class WebstatLogReader {

    private WebstatLogReader() {}

    public static Path latestLogPath() {
        return Bukkit.getWorldContainer().toPath().resolve("logs").resolve("latest.log");
    }

    public static TailResult readTail(int maxLines) throws IOException {
        Path path = latestLogPath();
        if (!Files.isRegularFile(path)) {
            return TailResult.missing(path);
        }
        long mtime = Files.getLastModifiedTime(path).toMillis();
        long size = Files.size(path);
        List<String> lines = readLastLines(path, maxLines);
        boolean truncated = size > 0 && lines.size() >= maxLines;
        return new TailResult(true, path.toString().replace('\\', '/'), mtime, size, lines, truncated);
    }

    public static byte[] readLastBytes(int maxBytes) throws IOException {
        Path path = latestLogPath();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        long size = Files.size(path);
        if (size <= 0) {
            return new byte[0];
        }
        long start = Math.max(0L, size - maxBytes);
        int len = (int) (size - start);
        byte[] buf = new byte[len];
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(start);
            raf.readFully(buf);
        }
        return buf;
    }

    private static List<String> readLastLines(Path path, int maxLines) throws IOException {
        // Read a capped tail chunk then split — avoids loading multi-100MB files.
        long size = Files.size(path);
        int chunk = (int) Math.min(size, Math.max(64_000L, (long) maxLines * 400L));
        byte[] buf = readLastBytes(chunk);
        if (buf == null || buf.length == 0) {
            return List.of();
        }
        String text = new String(buf, StandardCharsets.UTF_8);
        // If we started mid-line, drop the partial first line.
        if (size > chunk) {
            int nl = text.indexOf('\n');
            if (nl >= 0 && nl + 1 < text.length()) {
                text = text.substring(nl + 1);
            }
        }
        String[] raw = text.split("\\R", -1);
        List<String> all = new ArrayList<>(raw.length);
        for (String line : raw) {
            if (!line.isEmpty() || !all.isEmpty()) {
                all.add(line);
            }
        }
        if (!all.isEmpty() && all.get(all.size() - 1).isEmpty()) {
            all.remove(all.size() - 1);
        }
        if (all.size() <= maxLines) {
            return all;
        }
        return new ArrayList<>(all.subList(all.size() - maxLines, all.size()));
    }

    public record TailResult(
            boolean found,
            String path,
            long mtimeMs,
            long sizeBytes,
            List<String> lines,
            boolean truncated) {
        static TailResult missing(Path path) {
            return new TailResult(false, path.toString().replace('\\', '/'), 0L, 0L, Collections.emptyList(), false);
        }
    }
}
