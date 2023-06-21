package br.com.gradechecker.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

@WebFilter({
        "/home/*",
        "/detail/*",
        "/contributions-detail/*"
})
public class AuthenticationFilter implements Filter {

    private final Logger logger = LogManager.getLogger(this.getClass());

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        if (httpServletRequest.getRequestURI().contains("/login") || isUserLoggedOn(httpServletRequest)) {

            filterChain.doFilter(servletRequest, servletResponse);

        } else {

            servletRequest.setAttribute("hasMessage", true);
            servletRequest.setAttribute("message", "Access token not informed!");

            logger.info("Unauthorized: Access token not informed!");

            servletRequest.getRequestDispatcher("/authentication.jsp").forward(httpServletRequest, servletResponse);

        }

    }

    private boolean isUserLoggedOn(HttpServletRequest httpServletRequest) {

        return httpServletRequest.getSession().getAttribute("accessToken") != null;

    }

    private boolean isStaticAssets(HttpServletRequest httpServletRequest) {

        return httpServletRequest.getRequestURI().matches(".*[css|jpg|png|gif|js|webjars].*");

    }

}
