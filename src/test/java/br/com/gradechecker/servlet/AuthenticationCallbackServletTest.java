package br.com.gradechecker.servlet;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.*;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class AuthenticationCallbackServletTest {

    HttpServletRequest httpServletRequest;
    HttpServletResponse httpServletResponse;

    @Before
    public void init() {
        httpServletRequest = Mockito.mock(HttpServletRequest.class);
        httpServletResponse = Mockito.mock(HttpServletResponse.class);
    }

    @After
    public void end() {
        wireMockRule.stop();
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089); // No-args constructor defaults to port 8080

    @Test
    public void test_null_string() throws ServletException, IOException {

        mockServer();

        AuthenticationCallbackServlet authenticationCallbackServlet = new AuthenticationCallbackServlet();

        Mockito.when(httpServletRequest.getParameter("code")).thenReturn("12345");

        Mockito.doNothing().when(httpServletRequest).getSession().setAttribute("accessToken", Mockito.anyString());

        authenticationCallbackServlet.doGet(httpServletRequest, httpServletResponse);

        Mockito.verify(httpServletResponse).sendRedirect("/home");

        Assert.assertTrue(true);

    }

    public void mockServer() {

        wireMockRule.start();

        stubFor(post("/login/oauth/access_token")
                .withHeader("Accept", containing("application/json"))
                .willReturn(ok()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("github.json")
                ));

    }

}
