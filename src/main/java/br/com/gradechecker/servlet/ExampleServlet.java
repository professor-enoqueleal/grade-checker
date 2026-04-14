package br.com.gradechecker.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

@WebServlet("/example")
public class ExampleServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {

        httpServletResponse.setContentType("application/json");
        httpServletResponse.setCharacterEncoding("UTF-8");

        PrintWriter out = httpServletResponse.getWriter();

        URL resource = getClass().getClassLoader().getResource("data.json");

        try {
            String json = Files.readString(Paths.get(resource.toURI()));
            httpServletResponse.getWriter().print(json);
        } catch (Exception e) {
            httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            httpServletResponse.getWriter().print("{\"error\": \"Failed to read JSON\"}");
        }

        out.flush(); // Garante que o conteúdo seja enviado

    }

}
