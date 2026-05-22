package dev.restone0616.ymp.subtitle;

public class SubtitleItem {
    public long startMs;
    public long endMs;
    public String text;

    SubtitleItem(long start, long end, String text) {
        this.startMs = start;
        this.endMs = end;
        this.text = text;
    }
}
