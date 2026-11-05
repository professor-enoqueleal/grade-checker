package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Member;
import br.com.gradechecker.model.Contributor;
import br.com.gradechecker.proxy.GitHubProxy;
import br.com.gradechecker.repository.MemberRepository;
import br.com.gradechecker.repository.ContributorsRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/member/create")
public class CreateMemberServlet extends HttpServlet {

    private MemberRepository memberRepository;
    private GitHubProxy gitHubProxy;
    private ContributorsRepository contributorsRepository;

    public CreateMemberServlet() {
        this.memberRepository = new MemberRepository();
        this.gitHubProxy = new GitHubProxy();
        this.contributorsRepository = new ContributorsRepository();
    }

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        String name = httpServletRequest.getParameter("user-name");
        String gitHubUsername = httpServletRequest.getParameter("github-username");
        String groupId = httpServletRequest.getParameter("group-id");

        // validate github user
        Contributor ghUser = gitHubProxy.getUser(gitHubUsername);
        if (ghUser == null) {
            // redirect back with error
            httpServletResponse.sendRedirect("/detail?id=" + groupId + "&error=github_user_not_found");
            return;
        }

        // ensure contributor exists in CONTRIBUTORS table to satisfy FK
        contributorsRepository.createIfNotExists(ghUser, Long.parseLong(groupId));

        Member member = new Member(name, gitHubUsername, groupId, ghUser.getId());

        boolean created = memberRepository.createManual(member);

        if (!created) {
            httpServletResponse.sendRedirect("/detail?id=" + groupId + "&error=member_already_exists");
            return;
        }

        httpServletResponse.sendRedirect("/detail?id=" + groupId);

    }

}
