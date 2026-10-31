package dev.inbox.capture;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.Test;

public class TaskParserTest {

    // Friday 30 October 2026, 10:00.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 10, 0);

    private static String line(String spoken) {
        TaskParser.Task t = TaskParser.parse(spoken, NOW);
        return TaskFormatter.format(t, TaskFormatter.DEFAULT_LINE, TaskFormatter.DEFAULT_DUE,
                TaskFormatter.DEFAULT_DUE_TIME, NOW.toLocalDate());
    }

    @Test public void plainTask() {
        assertEquals("- [ ] Buy batteries", line("buy batteries"));
    }

    @Test public void stripsFillerAndPunctuation() {
        assertEquals("- [ ] Buy LED bulbs", line("Remind me to buy LED bulbs."));
        assertEquals("- [ ] Email Alex about the invoice", line("Okay, I need to email Alex about the invoice"));
        assertEquals("- [ ] Call the plumber", line("add a task to call the plumber please"));
    }

    @Test public void tomorrowWithTime() {
        assertEquals("- [ ] Call mom (@2026-10-31 17:00)", line("call mom tomorrow at 5pm"));
        assertEquals("- [ ] Call mom (@2026-10-31 17:00)", line("Tomorrow at 5 call mom"));
        assertEquals("- [ ] Call mom (@2026-10-31 17:30)", line("call mom tomorrow at 5:30 p.m."));
        assertEquals("- [ ] Call mom (@2026-10-31 09:00)", line("call mom tomorrow morning"));
    }

    @Test public void weekdays() {
        assertEquals("- [ ] Pay rent (@2026-11-02)", line("pay rent on Monday"));
        assertEquals("- [ ] Pay rent (@2026-11-06)", line("pay rent by friday"));     // today is Friday
        assertEquals("- [ ] Pay rent (@2026-10-30)", line("pay rent this friday"));
        assertEquals("- [ ] Pay rent (@2026-11-03)", line("pay rent next tuesday"));
    }

    @Test public void relative() {
        assertEquals("- [ ] Follow up (@2026-11-02)", line("follow up in 3 days"));
        assertEquals("- [ ] Follow up (@2026-11-13)", line("follow up in two weeks"));
        assertEquals("- [ ] Check oven (@2026-10-30 10:20)", line("check oven in 20 minutes"));
        assertEquals("- [ ] Check oven (@2026-10-30 11:00)", line("check oven in an hour"));
        assertEquals("- [ ] Review goals (@2026-11-02)", line("review goals next week"));
        assertEquals("- [ ] Review budget (@2026-11-01)", line("review budget next month"));
        assertEquals("- [ ] Clean garage (@2026-10-31)", line("clean garage this weekend"));
        assertEquals("- [ ] Submit invoice (@2026-10-31)", line("submit invoice by the end of the month"));
    }

    @Test public void absoluteDates() {
        assertEquals("- [ ] Dentist (@2026-11-05 14:00)", line("dentist on November 5th at 2pm"));
        assertEquals("- [ ] Dentist (@2026-11-05)", line("dentist on the 5th of November"));
        assertEquals("- [ ] Renew passport (@2027-03-01)", line("renew passport March 1"));      // past -> next year
        assertEquals("- [ ] Taxes (@2026-11-15)", line("taxes on the 15th"));
        assertEquals("- [ ] Taxes (@2026-12-15)", line("taxes due 2026-12-15"));
    }

    @Test public void timeOnly() {
        assertEquals("- [ ] Standup (@2026-10-30 14:00)", line("standup at 14:00"));
        assertEquals("- [ ] Take pills (@2026-10-31 08:00)", line("take pills at 8am"));  // past -> tomorrow
        assertEquals("- [ ] Take out bins (@2026-10-30 20:00)", line("take out bins tonight"));
        assertEquals("- [ ] Lunch with Sam (@2026-10-30 12:00)", line("lunch with Sam at noon"));
    }

    @Test public void leavesTaskWordsAlone() {
        assertEquals("- [ ] Plan morning run route", line("plan morning run route"));
        assertEquals("- [ ] Read Moby Dick", line("read Moby Dick"));
        assertEquals("- [ ] Buy 2 bags of soil", line("buy 2 bags of soil"));
    }

    // What on-device dictation actually produces: numbers as words, odd punctuation, mishearings.
    @Test public void spokenNumbers() {
        assertEquals("- [ ] Call the dentist (@2026-10-31 15:00)", line("Call the dentist tomorrow at three p.m."));
        assertEquals("- [ ] Call the dentist (@2026-10-30 15:00)", line("Call the dentist at three p.m"));
        assertEquals("- [ ] Call the dentist (@2026-10-31 15:30)", line("Call the dentist tomorrow at three thirty pm."));
        assertEquals("- [ ] Standup (@2026-10-30 16:30)", line("Standup at half past four."));
        assertEquals("- [ ] Leave (@2026-10-30 16:45)", line("Leave at quarter to five"));
        assertEquals("- [ ] Gym (@2026-10-31 07:00)", line("Gym tomorrow at seven a.m."));
        assertEquals("- [ ] Call Alex (@2026-10-30 14:00)", line("Call Alex at two o'clock"));
        assertEquals("- [ ] Send invoice (@2026-11-05)", line("Send invoice by November fifth."));
        assertEquals("- [ ] Book flights (@2026-12-12)", line("Book flights on December twelfth"));
        assertEquals("- [ ] Book flights (@2026-12-12)", line("Book flights on the twelfth of December."));
        assertEquals("- [ ] Pay rent (@2026-11-21)", line("Pay rent by the twenty-first"));
        assertEquals("- [ ] Check oven (@2026-10-30 10:20)", line("Check oven in twenty minutes"));
        assertEquals("- [ ] Follow up (@2026-11-13)", line("Follow up in fourteen days"));
    }

    @Test public void misheardAddATask() {
        assertEquals("- [ ] Go to the doctor (@2026-11-04)", line("At the task to go to the doctor on Wednesday."));
        assertEquals("- [ ] Buy milk", line("Add a task to buy milk"));
    }

    @Test public void numbersThatAreNotTimes() {
        assertEquals("- [ ] Look at one point in the report", line("look at one point in the report"));
        assertEquals("- [ ] Buy one bag of soil", line("buy one bag of soil"));
        assertEquals("- [ ] Visit at 5 shops", line("visit at 5 shops"));
        assertEquals("- [ ] Read the first chapter", line("read the first chapter"));
        assertEquals("- [ ] One day visit Zürich", line("one day visit Zürich"));
    }

    @Test public void customFormat() {
        TaskParser.Task t = TaskParser.parse("water plants tomorrow", NOW);
        assertEquals("- [ ] Water plants 📅 2026-10-31 ➕ 2026-10-30",
                TaskFormatter.format(t, "- [ ] {task}{due} ➕ {today}", " 📅 {date}", " 📅 {date} {time}",
                        LocalDate.of(2026, 10, 30)));
    }
}
