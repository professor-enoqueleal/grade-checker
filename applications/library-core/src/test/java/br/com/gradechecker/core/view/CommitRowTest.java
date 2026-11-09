package br.com.gradechecker.core.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** openspec capability commit-history-page: date split for phones and short hash. */
public class CommitRowTest {

    @Test
    public void splitsTheDayAndTheTime() {
        CommitRow row = new CommitRow("Oct 22, 2026 11:29", "m", "4ff70105f45d8a460c6f3f36291156ae7df49a01", null);
        assertEquals("Oct 22, 2026", row.getDay());
        assertEquals("11:29", row.getTime());
        assertEquals("4ff7010", row.getShortSha());
    }

    @Test
    public void toleratesDatesWithoutTimeAndShortOrMissingHashes() {
        CommitRow row = new CommitRow("", "m", "abc", null);
        assertEquals("", row.getDay());
        assertEquals("", row.getTime());
        assertEquals("abc", row.getShortSha());
        CommitRow none = new CommitRow(null, "m", null, null);
        assertEquals(null, none.getDay());
        assertEquals("", none.getTime());
        assertEquals(null, none.getShortSha());
    }
}
