package br.com.gradechecker.servlet;

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

            Connection connection = DriverManager.getConnection("jdbc:h2:~/test", "sa", "sa");

            RunScript.execute(connection, new FileReader("src/main/resources/db.sql"));

            logger.info("Success on load database tables");

        } catch (Exception e) {

            logger.error("Failed on load database tables. Cause: {}", e.getMessage());

        }

    }

}
