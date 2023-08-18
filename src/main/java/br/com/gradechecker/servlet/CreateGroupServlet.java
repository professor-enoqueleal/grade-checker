package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Group;
import br.com.gradechecker.repository.GroupRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/group/create")
public class CreateGroupServlet extends HttpServlet {

    private final GroupRepository groupRepository;

    public CreateGroupServlet() {
        this.groupRepository = new GroupRepository();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletRequest.getRequestDispatcher("/create-group.html").forward(httpServletRequest, httpServletResponse);

    }

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        String groupName = httpServletRequest.getParameter("group-name");
        String repositoryUrl = httpServletRequest.getParameter("repository-url");
        String repositoryOwner = httpServletRequest.getParameter("repository-owner");
        String repositoryName = httpServletRequest.getParameter("repository-name");

        Group group = new Group(groupName, repositoryUrl, repositoryOwner, repositoryName);

        groupRepository.create(group);

        httpServletResponse.sendRedirect("/home");

    }

}
