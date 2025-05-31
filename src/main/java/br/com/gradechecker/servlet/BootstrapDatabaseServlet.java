package br.com.gradechecker.servlet;

import br.com.gradechecker.config.ConnectionPoolConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.tools.RunScript;

import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;


@WebServlet(loadOnStartup = 1, urlPatterns = "/database")
public class BootstrapDatabaseServlet extends HttpServlet {

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void init() throws ServletException {

        loadDatabase();

    }

    public void loadDatabase() {

        try {

            Connection connection = ConnectionPoolConfig.getConnection();

            logger.info("############ TRYING LOAD DATABASE TABLES ############");

            RunScript.execute(connection, new FileReader("src/main/resources/db.sql"));

            logger.info("############ END LOAD DATABASE. TABLES LOADING WITH SUCCESS ############");

        } catch (Exception e) {

            logger.error("Failed on load database tables. Cause: {}", e.getMessage());

        }

    }

}
