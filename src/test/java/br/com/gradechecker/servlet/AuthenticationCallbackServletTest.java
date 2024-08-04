package br.com.gradechecker.servlet;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;

public class AuthenticationCallbackServletTest {

    HttpServletRequest httpServletRequest;
    HttpServletResponse httpServletResponse;
    HttpSession httpSession;

    @Before
    public void init() {
        httpServletRequest = mock(HttpServletRequest.class);
        httpServletResponse = mock(HttpServletResponse.class);
        httpSession = mock(HttpSession.class);
        startMockServer();
    }

    @After
    public void end() {
        wireMockRule.stop();
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089); // No-args constructor defaults to port 8080

    @Rule
    public EnvironmentVariables environmentVariablesRule = new EnvironmentVariables();

    @Test
    public void test_method_authenticated_with_success() throws ServletException, IOException, InterruptedException {

        environmentVariablesRule.set("GITHUB_BASE_URL", "http://localhost:8089");
        environmentVariablesRule.set("GITHUB_CLIENT_ID", "XYZ");
        environmentVariablesRule.set("GITHUB_CLIENT_SECRET", "1234");

        when(httpServletRequest.getParameter("code")).thenReturn("12345");
        when(httpServletRequest.getSession()).thenReturn(httpSession);

        doNothing().when(httpSession).setAttribute(any(), anyString());

        new AuthenticationCallbackServlet().doGet(httpServletRequest, httpServletResponse);

        verify(httpServletResponse).sendRedirect("/home");

        Assert.assertTrue(true);

    }

    public void startMockServer() {

        wireMockRule.start();

        stubFor(post("/login/oauth/access_token")
                .withHeader("Accept", containing("application/json"))
                .willReturn(ok()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("github_authenticated_with_success.json")
                ));

    }

}
