package br.com.gradechecker.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletRequest.getSession().invalidate();

        httpServletRequest.setAttribute("message", "Success on logout");

        httpServletRequest.getRequestDispatcher("/authentication.jsp").forward(httpServletRequest, httpServletResponse);

    }

}
