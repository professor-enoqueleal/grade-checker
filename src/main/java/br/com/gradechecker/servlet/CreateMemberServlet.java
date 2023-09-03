package br.com.gradechecker.servlet;

import br.com.gradechecker.model.Member;
import br.com.gradechecker.repository.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/member/create")
public class CreateMemberServlet extends HttpServlet {

    private MemberRepository memberRepository;

    public CreateMemberServlet() {
        this.memberRepository = new MemberRepository();
    }

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        String name = httpServletRequest.getParameter("user-name");
        String gitHubUsername = httpServletRequest.getParameter("github-username");
        String groupId = httpServletRequest.getParameter("group-id");

        memberRepository.create(new Member(name, gitHubUsername, groupId));

        httpServletResponse.sendRedirect("/detail?id=" + groupId);

    }

}
