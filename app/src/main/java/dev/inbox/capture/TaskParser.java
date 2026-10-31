package dev.inbox.capture;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls a due date/time out of a dictated sentence and tidies the rest into a task title.
 * Rule-based and offline. Plain Java (no Android imports) so it runs in JVM unit tests.
 */
public final class TaskParser {

    public static final class Task {
        public final String title;
        public final LocalDate date; // null when no date was said
        public final LocalTime time; // null when no time was said

        Task(String title, LocalDate date, LocalTime time) {
            this.title = title;
            this.date = date;
            this.time = time;
        }
    }

    private static final int F = Pattern.CASE_INSENSITIVE;
    private static final String WEEKDAY = "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)";
    private static final String MONTH =
            "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?"
                    + "|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)";
    private static final String NUM =
            "(\\d{1,4}|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|a couple of|couple of|a few|few)";
    // Words that only exist to introduce a date ("due friday", "by tomorrow"); swallowed with it.
    private static final String LEAD = "(?:\\b(?:on|by|before|due|for|until|this coming)\\s+)?";
    private static final String ORD = "(?:st|nd|rd|th)?";
    private static final String AMPM = "(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)";

    // Interjections are stripped one at a time in a loop: a repeated regex group recurses per
    // repetition in java.util.regex, and "um um um…" a few thousand times overflowed the stack.
    private static final Pattern INTERJECTION = Pattern.compile("^(?:hey|ok|okay|so|um+|uh+)\\b[\\s,]*", F);
    private static final Pattern FILLER = Pattern.compile(
            "^(?:please\\s+)?"
                    + "(?:remind me (?:to|that|about)|i need to|i have to|i've got to|i gotta|i must|"
                    // "at the task" / "ad a task" are how dictation tends to mishear "add a task".
                    + "don'?t forget to|remember to|(?:add|ad|at)(?: (?:a|an|the))?(?: new)? (?:task|to-?do|reminder)(?: (?:to|for|that))?|"
                    + "new (?:task|to-?do)|task|to-?do|note to self)\\b[\\s,:]*", F);

    private static final Pattern IN_MIN_HOUR = Pattern.compile(
            "\\bin\\s+" + NUM + "\\s+(minute|min|hour|hr)s?\\b", F);
    private static final Pattern IN_DAYS = Pattern.compile(
            "\\bin\\s+" + NUM + "\\s+(day|week|month|year)s?\\b", F);
    private static final Pattern DAY_AFTER_TOMORROW = Pattern.compile(LEAD + "\\b(?:the\\s+)?day after tomorrow\\b", F);
    private static final Pattern TOMORROW = Pattern.compile(LEAD + "\\btomorrow\\b", F);
    private static final Pattern TODAY = Pattern.compile(LEAD + "\\btoday\\b", F);
    private static final Pattern TONIGHT = Pattern.compile("\\btonight\\b", F);
    private static final Pattern WEEKEND = Pattern.compile(LEAD + "\\b(?:this\\s+|the\\s+)?weekend\\b", F);
    private static final Pattern END_OF = Pattern.compile(LEAD + "\\b(?:the\\s+)?end of (?:the\\s+|this\\s+)?(week|month)\\b", F);
    private static final Pattern NEXT_PERIOD = Pattern.compile(LEAD + "\\bnext\\s+(week|month|year)\\b", F);
    private static final Pattern WEEKDAY_P = Pattern.compile(LEAD + "\\b(?:(next|this)\\s+)?" + WEEKDAY + "\\b", F);
    private static final Pattern DAY_MONTH = Pattern.compile(
            LEAD + "\\b(?:the\\s+)?(\\d{1,2})" + ORD + "\\s+(?:of\\s+)?" + MONTH + "\\b(?:,?\\s+(\\d{4}))?", F);
    private static final Pattern MONTH_DAY = Pattern.compile(
            LEAD + "\\b" + MONTH + "\\s+(?:the\\s+)?(\\d{1,2})" + ORD + "\\b(?:,?\\s+(\\d{4}))?", F);
    private static final Pattern THE_NTH = Pattern.compile(LEAD + "\\bthe\\s+(\\d{1,2})(?:st|nd|rd|th)\\b", F);
    private static final Pattern ISO = Pattern.compile(LEAD + "\\b(\\d{4})-(\\d{2})-(\\d{2})\\b", F);

    private static final Pattern AT_TIME = Pattern.compile(
            "\\b(?:at|by|around|@)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(?:" + AMPM + "|o'?clock)?(?![\\w:])", F);
    private static final Pattern BARE_AMPM = Pattern.compile(
            "\\b(\\d{1,2})(?:[:.](\\d{2}))?\\s*" + AMPM + "(?![a-z])", F);
    private static final Pattern BARE_24H = Pattern.compile("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b", F);
    private static final Pattern NOON = Pattern.compile("\\b(?:at\\s+)?(noon|midday|midnight)\\b", F);
    private static final Pattern PART_OF_DAY = Pattern.compile(
            "\\b(?:(in the|this)\\s+)?(morning|afternoon|evening|night)\\b", F);

    // Spoken numbers. Dictation writes "three thirty p.m." and "December twelfth", so these are
    // turned into digits first, but only next to a time or date word: "buy one bag" stays as is.
    private static final String DIGIT = "one|two|three|four|five|six|seven|eight|nine";
    private static final String TEEN = "ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen";
    private static final String HOUR_W = "(\\d{1,2}|" + DIGIT + "|ten|eleven|twelve)";
    private static final String MIN_W =
            "((?:twenty|thirty|forty|fifty)(?:[- ](?:" + DIGIT + "))?|oh[- ](?:" + DIGIT + ")|" + TEEN + ")";
    private static final String CARD_W =
            "((?:twenty|thirty|forty|fifty)(?:[- ](?:" + DIGIT + "))?|" + TEEN + "|" + DIGIT + ")";
    private static final String ORD_W = "((?:twenty|thirty)[- ](?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)"
            + "|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth"
            + "|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth)";
    private static final String DAY_W = "(\\d{1,2}" + ORD + "|" + ORD_W.substring(1, ORD_W.length() - 1) + "|"
            + CARD_W.substring(1, CARD_W.length() - 1) + ")";

    private static final Pattern W_PAST = Pattern.compile("\\b(half|quarter) past " + HOUR_W + "\\b", F);
    private static final Pattern W_TO = Pattern.compile("\\b(?:a )?quarter to " + HOUR_W + "\\b", F);
    private static final Pattern W_CLOCK = Pattern.compile(
            "\\b" + HOUR_W + "(?:[ -]" + MIN_W + ")?(?=\\s*(?:o'?clock|a\\.?\\s?m\\b|p\\.?\\s?m\\b))", F);
    private static final Pattern W_AT = Pattern.compile(
            "\\b(at|by|around|until|before) " + HOUR_W + "(?:[ -]" + MIN_W + ")?\\b", F);
    private static final Pattern W_UNIT = Pattern.compile(
            "\\bin " + CARD_W + "(?= (?:minute|min|hour|hr|day|week|month|year)s?\\b)", F);
    private static final Pattern W_MONTH_DAY = Pattern.compile("\\b" + MONTH + " (?:the )?" + DAY_W + "\\b", F);
    private static final Pattern W_DAY_MONTH = Pattern.compile("\\b(?:the )?" + DAY_W + " (?:of )?" + MONTH + "\\b", F);
    private static final Pattern W_THE_NTH = Pattern.compile("\\b(on|by|before|until|due) the " + ORD_W + "\\b", F);
    // A bare "at 5" is only a time when nothing that could be its noun follows ("at 5 shops").
    private static final Pattern AFTER_BARE_HOUR = Pattern.compile(
            "\\s*(?:$|[,.;:!?]|(?:on|tomorrow|today|tonight|next|this|in|and|then|to|for|with|so|"
                    + "monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b)", F);

    private static final Pattern DANGLING = Pattern.compile(
            "(?:\\s+(?:on|at|by|for|in|and|the|before|until|due))+\\s*$", F);

    private final LocalDateTime now;
    private String s;
    private LocalDate date;
    private LocalTime time;

    private TaskParser(String input, LocalDateTime now) {
        this.s = " " + input.trim() + " ";
        this.now = now;
    }

    /** A task is a sentence; anything longer is cut, which also bounds the regex work. */
    static final int MAX_LENGTH = 300;

    public static Task parse(String input, LocalDateTime now) {
        String in = input == null ? "" : input.length() > MAX_LENGTH ? input.substring(0, MAX_LENGTH) : input;
        TaskParser p = new TaskParser(in, now);
        try {
            p.run();
            return new Task(p.title(), p.date, p.time);
        } catch (RuntimeException e) {
            // Never lose what was said over a parsing bug: keep it as the title, undated.
            return new Task(in.replaceAll("\\s+", " ").trim(), null, null);
        }
    }

    private void run() {
        String spoken = normalizeNumbers(s).trim();
        for (Matcher m = INTERJECTION.matcher(spoken); m.lookingAt(); m = INTERJECTION.matcher(spoken)) {
            spoken = spoken.substring(m.end());
        }
        s = " " + FILLER.matcher(spoken).replaceFirst("") + " ";
        LocalDate today = now.toLocalDate();

        Matcher m;
        if ((m = find(IN_MIN_HOUR)) != null) {
            int n = number(m.group(1));
            boolean hours = m.group(2).toLowerCase(Locale.ROOT).startsWith("h");
            LocalDateTime t = hours ? now.plusHours(n) : now.plusMinutes(n);
            date = t.toLocalDate();
            time = t.toLocalTime().withSecond(0).withNano(0);
            cut(m);
        }
        if (date == null && (m = find(IN_DAYS)) != null) {
            int n = number(m.group(1));
            switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "day": date = today.plusDays(n); break;
                case "week": date = today.plusWeeks(n); break;
                case "month": date = today.plusMonths(n); break;
                default: date = today.plusYears(n);
            }
            cut(m);
        }
        if (date == null && (m = find(DAY_AFTER_TOMORROW)) != null) { date = today.plusDays(2); cut(m); }
        if (date == null && (m = find(TOMORROW)) != null) { date = today.plusDays(1); cut(m); }
        if (date == null && (m = find(TODAY)) != null) { date = today; cut(m); }
        if ((m = find(TONIGHT)) != null) {
            if (date == null) date = today;
            if (time == null) time = LocalTime.of(20, 0);
            cut(m);
        }
        if (date == null && (m = find(WEEKEND)) != null) {
            date = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            cut(m);
        }
        if (date == null && (m = find(END_OF)) != null) {
            date = m.group(1).equalsIgnoreCase("week")
                    ? today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
                    : today.with(TemporalAdjusters.lastDayOfMonth());
            cut(m);
        }
        if (date == null && (m = find(NEXT_PERIOD)) != null) {
            switch (m.group(1).toLowerCase(Locale.ROOT)) {
                case "week": date = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); break;
                case "month": date = today.with(TemporalAdjusters.firstDayOfNextMonth()); break;
                default: date = today.with(TemporalAdjusters.firstDayOfNextYear());
            }
            cut(m);
        }
        if (date == null && (m = find(WEEKDAY_P)) != null) {
            DayOfWeek dow = DayOfWeek.valueOf(m.group(2).toUpperCase(Locale.ROOT));
            String mod = m.group(1) == null ? "" : m.group(1).toLowerCase(Locale.ROOT);
            if (mod.equals("next")) {
                // "next friday" = the friday of next week (Monday-based).
                date = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1)
                        .with(TemporalAdjusters.nextOrSame(dow));
            } else if (mod.equals("this")) {
                date = today.with(TemporalAdjusters.nextOrSame(dow));
            } else {
                date = today.with(TemporalAdjusters.next(dow));
            }
            cut(m);
        }
        if (date == null && (m = find(DAY_MONTH)) != null) {
            date = monthDay(month(m.group(2)), Integer.parseInt(m.group(1)), m.group(3), today);
            if (date != null) cut(m);
        }
        if (date == null && (m = find(MONTH_DAY)) != null) {
            date = monthDay(month(m.group(1)), Integer.parseInt(m.group(2)), m.group(3), today);
            if (date != null) cut(m);
        }
        if (date == null && (m = find(THE_NTH)) != null) {
            int d = Integer.parseInt(m.group(1));
            if (d >= 1 && d <= 31) {
                LocalDate base = d >= today.getDayOfMonth() ? today : today.plusMonths(1);
                date = base.withDayOfMonth(Math.min(d, base.lengthOfMonth()));
                cut(m);
            }
        }
        if (date == null && (m = find(ISO)) != null) {
            try {
                date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                cut(m);
            } catch (java.time.DateTimeException ignored) {
            }
        }

        parseTime();

        if (time != null && date == null) {
            date = time.isAfter(now.toLocalTime()) ? today : today.plusDays(1);
        }
    }

    private void parseTime() {
        Matcher m;
        if (time == null && (m = find(AT_TIME)) != null) {
            boolean bare = m.group(2) == null && m.group(3) == null && !m.group().toLowerCase(Locale.ROOT).contains("clock");
            LocalTime t = clock(m.group(1), m.group(2), m.group(3), true);
            if (t != null && (!bare || looksLikeTime(s, m))) {
                time = t;
                cut(m);
            }
        }
        if (time == null && (m = find(BARE_AMPM)) != null) {
            LocalTime t = clock(m.group(1), m.group(2), m.group(3), false);
            if (t != null) { time = t; cut(m); }
        }
        if (time == null && (m = find(BARE_24H)) != null) {
            time = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            cut(m);
        }
        if (time == null && (m = find(NOON)) != null) {
            time = m.group(1).equalsIgnoreCase("midnight") ? LocalTime.of(23, 59) : LocalTime.NOON;
            cut(m);
        }
        // "morning" alone is often part of the task ("morning run"), so only trust it next to a date
        // or with "in the"/"this".
        if ((m = find(PART_OF_DAY)) != null && (m.group(1) != null || date != null)) {
            if (time == null) {
                switch (m.group(2).toLowerCase(Locale.ROOT)) {
                    case "morning": time = LocalTime.of(9, 0); break;
                    case "afternoon": time = LocalTime.of(14, 0); break;
                    case "evening": time = LocalTime.of(19, 0); break;
                    default: time = LocalTime.of(20, 0);
                }
            }
            if (date == null && m.group(1) != null && m.group(1).equalsIgnoreCase("this")) {
                date = now.toLocalDate();
            }
            cut(m);
        }
    }

    /** @param assumePm when no am/pm was said, read 1-7 as afternoon ("at 5:30" means 17:30). */
    private static LocalTime clock(String h, String min, String ampm, boolean assumePm) {
        int hour = Integer.parseInt(h);
        int minute = min == null ? 0 : Integer.parseInt(min);
        if (hour > 23 || minute > 59) return null;
        if (ampm != null) {
            if (hour < 1 || hour > 12) return null;
            boolean pm = Character.toLowerCase(ampm.charAt(0)) == 'p';
            if (hour == 12) hour = pm ? 12 : 0;
            else if (pm) hour += 12;
        } else if (assumePm && hour >= 1 && hour <= 7 && !h.startsWith("0")) {
            hour += 12;
        }
        return LocalTime.of(hour, minute);
    }

    private static String normalizeNumbers(String in) {
        String out = replace(in, W_PAST, m -> {
            int h = wordNumber(m.group(2));
            return h + (m.group(1).equalsIgnoreCase("half") ? ":30" : ":15");
        });
        out = replace(out, W_TO, m -> {
            int h = wordNumber(m.group(1));
            return (h == 1 ? 12 : h - 1) + ":45";
        });
        out = replace(out, W_CLOCK, m -> clockDigits(m.group(1), m.group(2)));
        String beforeAt = out;
        out = replace(beforeAt, W_AT, m -> m.group(3) == null && !looksLikeTime(beforeAt, m)
                ? m.group() // "look at one point" is not a time
                : m.group(1) + " " + clockDigits(m.group(2), m.group(3)));
        out = replace(out, W_UNIT, m -> "in " + wordNumber(m.group(1)));
        out = replace(out, W_MONTH_DAY, m -> m.group(1) + " " + dayDigits(m.group(2)));
        out = replace(out, W_DAY_MONTH, m -> dayDigits(m.group(1)) + " of " + m.group(2));
        out = replace(out, W_THE_NTH, m -> m.group(1) + " the " + dayDigits(m.group(2)));
        return out;
    }

    /** A bare "at 5" leading the sentence, or followed by nothing noun-like, is a time. */
    private static boolean looksLikeTime(String text, Matcher m) {
        return text.substring(0, m.start()).trim().isEmpty()
                || AFTER_BARE_HOUR.matcher(text).region(m.end(), text.length()).lookingAt();
    }

    private static String clockDigits(String hour, String minute) {
        String h = String.valueOf(wordNumber(hour));
        if (hour.matches("\\d+")) h = hour; // keep "05" so it isn't read as afternoon
        if (minute == null) return h;
        int min = wordNumber(minute.replaceFirst("(?i)^oh[- ]", ""));
        return h + ":" + (min < 10 ? "0" : "") + min;
    }

    private static String dayDigits(String day) {
        if (Character.isDigit(day.charAt(0))) return day;
        int n = wordNumber(day);
        int mod = n % 100;
        String suffix = mod >= 11 && mod <= 13 ? "th"
                : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suffix;
    }

    /** "twenty-first" -> 21, "fifteen" -> 15, "7" -> 7. */
    private static int wordNumber(String word) {
        String w = word.toLowerCase(Locale.ROOT).trim();
        if (Character.isDigit(w.charAt(0))) return Integer.parseInt(w.replaceAll("\\D.*", ""));
        int total = 0;
        for (String part : w.split("[- ]+")) {
            switch (part) {
                case "twenty": case "twentieth": total += 20; break;
                case "thirty": case "thirtieth": total += 30; break;
                case "forty": total += 40; break;
                case "fifty": total += 50; break;
                default: total += smallNumber(part);
            }
        }
        return total;
    }

    private static int smallNumber(String w) {
        String[][] names = {
                {"one", "first"}, {"two", "second"}, {"three", "third"}, {"four", "fourth"}, {"five", "fifth"},
                {"six", "sixth"}, {"seven", "seventh"}, {"eight", "eighth"}, {"nine", "ninth"}, {"ten", "tenth"},
                {"eleven", "eleventh"}, {"twelve", "twelfth"}, {"thirteen", "thirteenth"}, {"fourteen", "fourteenth"},
                {"fifteen", "fifteenth"}, {"sixteen", "sixteenth"}, {"seventeen", "seventeenth"},
                {"eighteen", "eighteenth"}, {"nineteen", "nineteenth"}};
        for (int i = 0; i < names.length; i++) {
            if (names[i][0].equals(w) || names[i][1].equals(w)) return i + 1;
        }
        return 0;
    }

    private static String replace(String in, Pattern p, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(in);
        StringBuffer out = new StringBuffer(); // the StringBuilder overloads need API 34
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(f.apply(m)));
        m.appendTail(out);
        return out.toString();
    }

    private static LocalDate monthDay(int month, int day, String year, LocalDate today) {
        try {
            if (year != null) return LocalDate.of(Integer.parseInt(year), month, day);
            LocalDate d = LocalDate.of(today.getYear(), month, day);
            return d.isBefore(today) ? d.plusYears(1) : d;
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    private static int month(String name) {
        String n = name.toLowerCase(Locale.ROOT).substring(0, 3);
        return "janfebmaraprmayjunjulaugsepoctnovdec".indexOf(n) / 3 + 1;
    }

    private static int number(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (Character.isDigit(w.charAt(0))) return Integer.parseInt(w);
        if (w.contains("couple")) return 2;
        if (w.contains("few")) return 3;
        String[] words = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"};
        for (int i = 0; i < words.length; i++) if (words[i].equals(w)) return i + 1;
        return 1; // "a", "an"
    }

    private Matcher find(Pattern p) {
        Matcher m = p.matcher(s);
        return m.find() ? m : null;
    }

    private void cut(Matcher m) {
        s = s.substring(0, m.start()) + " " + s.substring(m.end());
    }

    private String title() {
        String t = s.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("\\s+([,.;:!?])", "$1").replaceAll("([,;:])\\s*(?=[,;:.!?])", "");
        t = t.replaceAll("^[\\s,;:.!?-]+", "").replaceAll("[\\s,;:-]+$", "");
        t = DANGLING.matcher(t).replaceAll("");
        t = t.replaceAll("[.!]+$", "").replaceAll("(?i)[,\\s]+please$", "").trim();
        if (t.isEmpty()) return t;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
