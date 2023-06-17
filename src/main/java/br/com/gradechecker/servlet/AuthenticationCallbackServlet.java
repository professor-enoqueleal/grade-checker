package br.com.gradechecker.servlet;

import br.com.gradechecker.model.AccessToken;
import br.com.gradechecker.proxy.AuthenticationProxy;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/callback")
public class AuthenticationCallbackServlet extends HttpServlet {

    private AuthenticationProxy authenticationProxy;

    public AuthenticationCallbackServlet() {
        this.authenticationProxy = new AuthenticationProxy();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        String code = httpServletRequest.getParameter("code");

        if (null != code) {

            AccessToken accessToken = authenticationProxy.exchangeCodePerToken(code);

            httpServletRequest.getSession().setAttribute("accessToken", accessToken.getAccessToken());

            httpServletResponse.sendRedirect("/home");

        } else {

            String errorDescription = httpServletRequest.getParameter("error_description");

            httpServletRequest.setAttribute("message", errorDescription);

            httpServletRequest.getRequestDispatcher("/authentication.jsp").forward(httpServletRequest, httpServletResponse);

        }

    }

}
