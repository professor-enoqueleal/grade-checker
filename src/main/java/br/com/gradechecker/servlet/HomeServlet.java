package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Group;
import br.com.gradechecker.repository.GroupRepository;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = "/home")
public class HomeServlet extends HttpServlet {

    private GroupRepository groupRepository;

//    @Inject
//    private GradeServiceImpl gradeService;

    public HomeServlet() {
        this.groupRepository = new GroupRepository();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletRequest.setAttribute("message", "Servlet is running!");

        List<Group> groups = groupRepository.findAll();

        httpServletRequest.setAttribute("groups", groups);

        RequestDispatcher requestDispatcher = httpServletRequest.getRequestDispatcher("home.jsp");

        requestDispatcher.forward(httpServletRequest, httpServletResponse);

    }

}
