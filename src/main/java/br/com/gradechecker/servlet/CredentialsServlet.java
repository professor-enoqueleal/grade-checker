package br.com.gradechecker.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/credentials")
public class CredentialsServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        final String accessToken = httpServletRequest.getParameter("access-token");

        if (accessToken.isBlank()) {

            throw new RuntimeException("Missing Access Token");
        }

        httpServletRequest.getSession().setAttribute("accessToken", accessToken);

        httpServletResponse.sendRedirect("/home");

    }

}
