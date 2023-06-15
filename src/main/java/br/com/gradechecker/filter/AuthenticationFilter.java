package br.com.gradechecker.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

@WebFilter("/*")
public class AuthenticationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        if (httpServletRequest.getRequestURI().contains("/credentials") || isUserLoggedOn(httpServletRequest)) {

            filterChain.doFilter(servletRequest, servletResponse);

        } else {

            servletRequest.setAttribute("message", "Access token not informed!");

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
