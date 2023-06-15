package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.model.Group;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.GroupRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/detail")
public class GroupDetailServlet extends HttpServlet {

    private GroupRepository groupRepository;

    public GroupDetailServlet() {
        this.groupRepository = new GroupRepository();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        String groupId = httpServletRequest.getParameter("id");

        if (null == groupId) {
            throw new RuntimeException("Error! Group id is not informed");
        }

        Group group = groupRepository.getGroupDetail(Long.parseLong(groupId));

        String accessToken = (String) httpServletRequest.getSession().getAttribute("accessToken");

        List<Contributor> contributors = new GitHubProxy().getContributors(accessToken, group.getOwner(), group.getRepository());

        httpServletRequest.setAttribute("group", group);
        httpServletRequest.setAttribute("contributors", contributors);

        httpServletRequest.getRequestDispatcher("detail.jsp").forward(httpServletRequest, httpServletResponse);

    }

}
