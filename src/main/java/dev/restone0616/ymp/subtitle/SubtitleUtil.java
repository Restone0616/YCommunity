package dev.restone0616.ymp.subtitle;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubtitleUtil {
    private SubtitleUtil() {}

    public static @NotNull List<SubtitleItem> parseSRT(@NotNull File file) {
        List<SubtitleItem> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Pattern timePattern = Pattern.compile(
                    "(\\d{2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})");
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String timeLine = reader.readLine();
                if (timeLine == null) break;
                Matcher m = timePattern.matcher(timeLine);
                if (!m.matches()) continue;
                long start = Long.parseLong(m.group(1)) * 3600000 +
                        Long.parseLong(m.group(2)) * 60000 +
                        Long.parseLong(m.group(3)) * 1000 +
                        Long.parseLong(m.group(4));
                long end = Long.parseLong(m.group(5)) * 3600000 +
                        Long.parseLong(m.group(6)) * 60000 +
                        Long.parseLong(m.group(7)) * 1000 +
                        Long.parseLong(m.group(8));
                StringBuilder textBuilder = new StringBuilder();
                while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
                    textBuilder.append(line).append("\n");
                }
                String text = textBuilder.toString().trim();
                items.add(new SubtitleItem(start, end, text));
            }
        } catch (Exception ignored) {}
        return items;
    }
}
