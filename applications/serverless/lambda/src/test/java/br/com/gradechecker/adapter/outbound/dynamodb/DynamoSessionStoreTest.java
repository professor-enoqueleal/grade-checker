package br.com.gradechecker.adapter.outbound.dynamodb;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Extension of the idle deadline, without DynamoDB (openspec capability serverless-session-auth). */
public class DynamoSessionStoreTest {

    private static final long IDLE = 30 * 60;
    private static final long LOGIN = 1_000_000;
    private static final long MAX = LOGIN + 8 * 60 * 60;

    @Test
    public void aUseMovesTheIdleDeadline() {
        long expiresAt = LOGIN + IDLE;
        assertEquals(LOGIN + 600 + IDLE, DynamoSessionStore.extendedDeadline(LOGIN + 600, expiresAt, MAX, IDLE));
    }

    @Test
    public void atMostOneWritePerMinute() {
        long expiresAt = LOGIN + IDLE;
        assertEquals(-1, DynamoSessionStore.extendedDeadline(LOGIN + 59, expiresAt, MAX, IDLE));
        assertEquals(LOGIN + 60 + IDLE, DynamoSessionStore.extendedDeadline(LOGIN + 60, expiresAt, MAX, IDLE));
    }

    @Test
    public void neverPastTheAbsoluteDeadline() {
        long now = MAX - 10 * 60;
        long expiresAt = now - 25 * 60 + IDLE;         // last write 25 minutes ago: 5 minutes left
        assertEquals(MAX, DynamoSessionStore.extendedDeadline(now, expiresAt, MAX, IDLE));
        assertEquals(-1, DynamoSessionStore.extendedDeadline(MAX - 60, MAX, MAX, IDLE));
    }

    @Test
    public void oldSessionsUseTheirExpiresAtAsTheLimit() {
        long expiresAt = LOGIN + 8 * 60 * 60;          // created with the fixed 8 hours
        assertEquals(-1, DynamoSessionStore.extendedDeadline(LOGIN + 600, expiresAt, expiresAt, IDLE));
    }
}
