package dev.inbox.capture;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Renders a parsed task into a Markdown line. Placeholders:
 * {task}, {due} (expands to one of the due templates, or nothing), {date}, {time}, {today}.
 * Defaults match the Obsidian Reminder plugin: {@code - [ ] Call mom (@2026-11-02 17:00)}.
 */
public final class TaskFormatter {

    public static final String DEFAULT_LINE = "- [ ] {task}{due}";
    public static final String DEFAULT_DUE = " (@{date})";
    public static final String DEFAULT_DUE_TIME = " (@{date} {time})";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private TaskFormatter() {}

    /**
     * Task text is never markup: backticks (code, Dataview inline JS), HTML (remote images) and
     * markdown images (remote fetches when the note is rendered) are escaped. Dictation never
     * produces these; shared text from other apps might.
     */
    static String escape(String title) {
        return title.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("<", "\\<")
                .replace("![", "!\\[");
    }

    public static String format(TaskParser.Task task, String line, String due, String dueTime, LocalDate today) {
        String dueText = "";
        if (task.date != null) {
            dueText = (task.time != null ? dueTime : due)
                    .replace("{date}", task.date.toString())
                    .replace("{time}", task.time != null ? task.time.format(TIME) : "");
        }
        return line.replace("{due}", dueText)
                .replace("{date}", task.date != null ? task.date.toString() : "")
                .replace("{time}", task.time != null ? task.time.format(TIME) : "")
                .replace("{today}", today.toString())
                .replace("{task}", escape(task.title))
                .replaceAll("\\s+$", "");
    }
}
