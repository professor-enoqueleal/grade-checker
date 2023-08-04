package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Commit;
import br.com.gradechecker.model.CommitRequestBody;
import br.com.gradechecker.proxy.GitHubProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

@WebServlet("/contributions-detail")
public class ContributionsDetailServlet extends HttpServlet {

    private final GitHubProxy gitHubProxy;

    public ContributionsDetailServlet() {
        this.gitHubProxy = new GitHubProxy();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        buildBody(httpServletRequest, httpServletResponse);

    }

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        buildBody(httpServletRequest, httpServletResponse);

    }

    private void buildBody(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        CommitRequestBody commitRequestBody = getParameters(httpServletRequest);

        List<Commit> commits = gitHubProxy.getCommits(commitRequestBody);

        httpServletRequest.setAttribute("author", commitRequestBody.getCommitAuthor());
        httpServletRequest.setAttribute("commits", commits);
        httpServletRequest.setAttribute("since", commitRequestBody.getSince());
        httpServletRequest.setAttribute("until", commitRequestBody.getUntil());
        httpServletRequest.setAttribute("groupId", commitRequestBody.getGroupId());

        httpServletRequest.getRequestDispatcher("commits-detail.jsp").forward(httpServletRequest, httpServletResponse);

    }

    private CommitRequestBody getParameters(HttpServletRequest httpServletRequest) {

        String accessToken = (String) httpServletRequest.getSession().getAttribute("accessToken");

        String repository = httpServletRequest.getParameter("repo");
        String owner = httpServletRequest.getParameter("owner");
        String author = httpServletRequest.getParameter("author");
        String groupId = httpServletRequest.getParameter("groupId");
        String since = null == httpServletRequest.getParameter("date-since") ? "2023-01-1T00:00:00Z" : httpServletRequest.getParameter("date-since");
        String until = null == httpServletRequest.getParameter("date-until") ? Instant.now().toString() : httpServletRequest.getParameter("date-until");

        return new CommitRequestBody(repository, owner, author, since, until, accessToken, groupId);

    }

}
