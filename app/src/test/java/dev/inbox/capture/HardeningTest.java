package dev.inbox.capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

public class HardeningTest {

    private static String line(String text) {
        TaskParser.Task t = TaskParser.parse(text, LocalDateTime.of(2026, 10, 30, 10, 0));
        return TaskFormatter.format(t, TaskFormatter.DEFAULT_LINE, TaskFormatter.DEFAULT_DUE,
                TaskFormatter.DEFAULT_DUE_TIME, LocalDate.of(2026, 10, 30));
    }

    @Test public void markupInTasksIsInert() {
        assertEquals("- [ ] Run \\`$= dv.io.load('x')\\`", line("run `$= dv.io.load('x')`"));
        assertEquals("- [ ] See \\<img src=https://evil.example/t.gif>", line("see <img src=https://evil.example/t.gif>"));
        assertEquals("- [ ] Look!\\[](https://evil.example/p.png)", line("look ![](https://evil.example/p.png)"));
        assertEquals("- [ ] Path C:\\\\temp", line("path C:\\temp"));
        assertEquals("- [ ] Plain words stay plain (@2026-10-31)", line("plain words stay plain tomorrow"));
    }

    @Test public void sharedTextIsCapped() {
        String many = "task\n".repeat(500);
        assertEquals(CaptureActivity.MAX_SHARED_LINES, CaptureActivity.limitShared(many).split("\n").length);
        String huge = "x".repeat(100_000);
        assertTrue(CaptureActivity.limitShared(huge).length() <= CaptureActivity.MAX_SHARED_CHARS);
        assertEquals("a\nb", CaptureActivity.limitShared("  a \r\n\r\n\n b  "));
        assertFalse(CaptureActivity.limitShared("\n\n\n").contains("\n"));
    }
}
