package br.com.gradechecker.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Closes the application database when the web application stops (redeploy, systemctl restart, container stop,
 * mvn jetty:run with SIGTERM), so the last writes are saved to disk (openspec capability database-bootstrap).
 */
@WebListener
public class DatabaseShutdownListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        ConnectionPoolConfig.shutdown();
    }

}
