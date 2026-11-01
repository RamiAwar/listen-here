package io.github.ramiawar.listenhere;

import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.Random;

import org.junit.Test;

/** Hostile input must never crash the parser or make it hang (shared text reaches it unfiltered). */
public class ParserFuzzTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    private static void survive(String input) {
        long start = System.nanoTime();
        try {
            TaskParser.parse(input, NOW);
        } catch (Throwable t) {
            throw new AssertionError(t.getClass().getSimpleName() + " on: [" + input + "]", t);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("took " + ms + " ms on: " + input.substring(0, Math.min(60, input.length())), ms < 500);
    }

    @Test public void hugeNumbers() {
        survive("follow up in 99999999999 days");
        survive("follow up in 999999999 years");
        survive("in 2147483647 weeks");
        survive("check in 99999999999999999999 minutes");
        survive("pay rent on the 99th");
        survive("due 9999-99-99");
        survive("at 99:99");
        survive("at 999");
        survive("November 99th 99999");
    }

    @Test public void pathologicalRepetition() {
        String[] units = {"um ", "uh ", "at ", "in ", "the ", "on ", "by ", "a ", "twenty ", "one ", "1 ", ", ", "p.m. ", "@ ", "o'clock "};
        for (String u : units) survive(u.repeat(5000) + "x");
        survive("ummmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm".repeat(200) + "!");
        survive("1".repeat(100000));
        survive(" ".repeat(100000));
    }

    @Test public void random() {
        Random r = new Random(42);
        String[] words = {"at", "in", "on", "by", "the", "of", "next", "this", "tomorrow", "today", "friday",
                "november", "may", "twenty", "first", "one", "half", "past", "quarter", "to", "pm", "a.m.",
                "o'clock", "noon", "99", "12", "3:30", "2026-02-30", "days", "minutes", "-", ",", ".", "remind", "me"};
        for (int i = 0; i < 20000; i++) {
            StringBuilder sb = new StringBuilder();
            int n = r.nextInt(12);
            for (int j = 0; j < n; j++) sb.append(words[r.nextInt(words.length)]).append(' ');
            survive(sb.toString());
        }
    }
}
