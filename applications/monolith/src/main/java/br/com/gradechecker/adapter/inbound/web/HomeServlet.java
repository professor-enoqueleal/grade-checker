package br.com.gradechecker.adapter.inbound.web;

import br.com.gradechecker.config.AdminConfig;
import br.com.gradechecker.config.AppContext;
import br.com.gradechecker.core.application.GroupManagement;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = "/home")
public class HomeServlet extends HttpServlet {

    private final GroupManagement groups;

    public HomeServlet() {
        this(AppContext.get().groups);
    }

    public HomeServlet(GroupManagement groups) {
        this.groups = groups;
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletRequest.setAttribute("message", "Servlet is running!");

        httpServletRequest.setAttribute("groups", groups.activeGroups());

        // link "Class grades" only for administrators (openspec capability class-gradebook)
        httpServletRequest.setAttribute("isAdmin", AdminConfig.isAdmin(httpServletRequest.getSession(false)));

        RequestDispatcher requestDispatcher = httpServletRequest.getRequestDispatcher("home.jsp");

        requestDispatcher.forward(httpServletRequest, httpServletResponse);

    }

}
