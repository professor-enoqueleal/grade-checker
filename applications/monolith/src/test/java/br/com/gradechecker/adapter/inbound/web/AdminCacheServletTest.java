package br.com.gradechecker.adapter.inbound.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** openspec capability in-memory-cache: admin API authorization and registration. */
public class AdminCacheServletTest {

    private static final String SECRET = "GCACHE_ADMIN_SECRET";

    @After
    public void clear() {
        System.clearProperty(SECRET);
    }

    private static boolean authorized(String header) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-ADMIN-TOKEN")).thenReturn(header);
        return new AdminCacheServlet().authorize(request);
    }

    @Test
    public void acceptsOnlyTheExactToken() {
        System.setProperty(SECRET, "s3cret");

        assertTrue(authorized("s3cret"));
        assertFalse(authorized("s3cre"));
        assertFalse(authorized("s3cret!"));
        assertFalse(authorized("wrong"));
        assertFalse(authorized(null));
    }

    @Test
    public void deniesEverythingWithoutConfiguredSecret() {
        System.clearProperty(SECRET);
        // environment of the test JVM does not define GCACHE_ADMIN_SECRET
        assumeNoEnvSecret();
        assertFalse(authorized("anything"));
        assertFalse(authorized(""));
    }

    @Test
    public void registeredOnceByAnnotationWithBothRoutes() throws Exception {
        WebServlet annotation = AdminCacheServlet.class.getAnnotation(WebServlet.class);
        assertEquals(Arrays.asList("/admin/cache", "/admin/cache/stats"), Arrays.asList(annotation.urlPatterns()));

        String webXml = Files.readString(Path.of("src/main/webapp/WEB-INF/web.xml"))
                .replaceAll("(?s)<!--.*?-->", "");
        assertFalse("AdminCacheServlet must not be declared in web.xml", webXml.contains("AdminCacheServlet"));
    }

    private static void assumeNoEnvSecret() {
        String env = System.getenv(SECRET);
        org.junit.Assume.assumeTrue(env == null || env.isEmpty());
    }
}
