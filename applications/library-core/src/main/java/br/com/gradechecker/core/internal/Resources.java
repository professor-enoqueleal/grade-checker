package br.com.gradechecker.core.internal;

import java.io.InputStream;

/**
 * Configuration files of the application (grading.properties, milestones.properties): they stay in each application
 * and are read through the context class loader of the thread (the web application in the Jetty version, the function
 * in the Lambda), with the class loader of the library as fallback. Internal: not part of the API of the library.
 */
public final class Resources {

    private Resources() {
    }

    /** The resource ("/grading.properties"), or null when it does not exist. */
    public static InputStream open(String resource, Class<?> fallback) {
        String name = resource.startsWith("/") ? resource.substring(1) : resource;
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream in = context == null ? null : context.getResourceAsStream(name);
        return in != null ? in : fallback.getResourceAsStream(resource);
    }
}
