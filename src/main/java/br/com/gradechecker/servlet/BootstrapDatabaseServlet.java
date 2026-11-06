package br.com.gradechecker.servlet;

import br.com.gradechecker.config.ConnectionPoolConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.tools.RunScript;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * Creates the schema and seed data at startup from the db.sql packaged in the application
 * (openspec capability database-bootstrap). Tables are recreated at every start (tech debt #2).
 */
@WebServlet(loadOnStartup = 1, urlPatterns = "/database")
public class BootstrapDatabaseServlet extends HttpServlet {

    static final String SCRIPT = "/db.sql";

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void init() throws ServletException {

        loadDatabase();

    }

    public void loadDatabase() {

        loadDatabase(SCRIPT);

    }

    /** Runs the given classpath script; returns false (and logs the cause) when it cannot be loaded. */
    boolean loadDatabase(String classpathScript) {

        InputStream script = BootstrapDatabaseServlet.class.getResourceAsStream(classpathScript);

        if (script == null) {
            logger.error("Failed on load database tables. Cause: {} not found on the classpath", classpathScript.substring(1));
            return false;
        }

        try (Reader reader = new InputStreamReader(script, StandardCharsets.UTF_8);
             Connection connection = ConnectionPoolConfig.getConnection()) {

            logger.info("############ TRYING LOAD DATABASE TABLES ############");

            RunScript.execute(connection, reader);

            logger.info("############ END LOAD DATABASE. TABLES LOADING WITH SUCCESS ############");

            return true;

        } catch (Exception e) {

            logger.error("Failed on load database tables. Cause: {}", e.getMessage());

            return false;

        }

    }

}
