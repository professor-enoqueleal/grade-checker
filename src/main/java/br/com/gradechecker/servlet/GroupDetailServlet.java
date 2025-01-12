package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Group;
import br.com.gradechecker.service.GroupService;
import br.com.gradechecker.service.GroupServiceImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/detail")
public class GroupDetailServlet extends HttpServlet {

    private final GroupService groupService;

    public GroupDetailServlet() {
        this.groupService = new GroupServiceImpl();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        String groupId = httpServletRequest.getParameter("id");

        if (null == groupId) {
            throw new RuntimeException("Error! Group id is not informed");
        }

        String accessToken = (String) httpServletRequest.getSession().getAttribute("accessToken");

        Group group = groupService.getGroup(Long.parseLong(groupId), accessToken);

        httpServletRequest.setAttribute("group", group);

        httpServletRequest.getRequestDispatcher("detail.jsp").forward(httpServletRequest, httpServletResponse);

    }

}
