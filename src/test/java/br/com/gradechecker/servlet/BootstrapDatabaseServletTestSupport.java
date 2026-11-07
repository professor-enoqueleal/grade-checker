package br.com.gradechecker.servlet;

/** Points the pool to the shared in-memory test database and runs db.sql (idempotent, safe to repeat). */
public final class BootstrapDatabaseServletTestSupport {

    private BootstrapDatabaseServletTestSupport() {
    }

    public static void loadSchema() {
        System.setProperty("DB_URL", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        System.setProperty("DB_USER", "sa");
        System.setProperty("DB_PASS", "");
        if (!new BootstrapDatabaseServlet().loadDatabase(BootstrapDatabaseServlet.SCRIPT)) {
            throw new IllegalStateException("db.sql could not be loaded");
        }
    }

    /** Runs db.sql again on the same database (a restart). */
    public static boolean reload() {
        return new BootstrapDatabaseServlet().loadDatabase(BootstrapDatabaseServlet.SCRIPT);
    }
}
