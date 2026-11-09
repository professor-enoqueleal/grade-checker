package br.com.gradechecker.core;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Dependency rules of the library (capabilities core-boundaries and core-structure), read from the sources:
 * <ul>
 *   <li>no infrastructure of the applications anywhere;</li>
 *   <li>Unirest and Gson only in core.adapter (the GitHub client);</li>
 *   <li>ports and domain errors import only the JDK and the library;</li>
 *   <li>use cases never depend on an adapter;</li>
 *   <li>no cycle between the packages.</li>
 * </ul>
 */
public class ArchitectureTest {

    private static final Path SOURCES = Paths.get("src/main/java/br/com/gradechecker/core");

    /** Never in the library: web server, JDBC, AWS, Caffeine, Log4j (the applications bring them). */
    private static final List<String> FORBIDDEN = List.of(
            "jakarta.", "javax.servlet.", "java.sql.", "software.amazon.", "com.github.benmanes.", "org.apache.logging.");

    /** Only in core.adapter (the GitHub client). */
    private static final List<String> ADAPTER_ONLY = List.of("kong.unirest.", "com.google.gson.");

    private static final String LIBRARY = "br.com.gradechecker.core.";

    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?([\\w.]+)(?:\\.\\*)?;\\s*$");
    private static final Pattern REFERENCE = Pattern.compile("(?<![\\w.])((?:[a-z]\\w*\\.)+)[A-Za-z_*]");

    @Test
    public void noInfrastructureOfTheApplications() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sources()) {
            for (String reference : references(file)) {
                for (String forbidden : FORBIDDEN) {
                    if (reference.startsWith(forbidden)) violations.add(SOURCES.relativize(file) + ": " + reference);
                }
            }
        }
        assertEquals("infrastructure inside the library", List.of(), violations);
    }

    @Test
    public void unirestAndGsonOnlyInTheAdapter() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sources()) {
            if (SOURCES.relativize(file).startsWith("adapter")) continue;
            for (String reference : references(file)) {
                for (String library : ADAPTER_ONLY) {
                    if (reference.startsWith(library)) violations.add(SOURCES.relativize(file) + ": " + reference);
                }
            }
        }
        assertEquals("Unirest/Gson outside core.adapter", List.of(), violations);
    }

    /** Use cases (capability core-structure): GitHub only through the port GitHubGateway. */
    @Test
    public void useCasesNeverDependOnAnAdapter() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : packageDependencies().entrySet()) {
            if (!e.getKey().equals("application")) continue;
            for (String used : e.getValue()) if (used.startsWith("adapter")) violations.add("application -> " + used);
        }
        assertEquals("use cases depending on an adapter", List.of(), violations);
    }

    /** Capability core-structure: the packages form a graph without cycles. */
    @Test
    public void noCycleBetweenPackages() throws IOException {
        Map<String, Set<String>> graph = packageDependencies();
        Set<String> done = new HashSet<>();
        for (String start : graph.keySet()) {
            List<String> cycle = cycleFrom(start, graph, new ArrayList<>(), done);
            assertEquals("cycle between packages", List.of(), cycle);
        }
    }

    /** Depth-first search; the cycle found (e.g. [port, github, port]) or an empty list. */
    private static List<String> cycleFrom(String node, Map<String, Set<String>> graph, List<String> path, Set<String> done) {
        int index = path.indexOf(node);
        if (index >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(index, path.size()));
            cycle.add(node);
            return cycle;
        }
        if (done.contains(node)) return List.of();
        path.add(node);
        for (String next : graph.getOrDefault(node, Set.of())) {
            List<String> cycle = cycleFrom(next, graph, path, done);
            if (!cycle.isEmpty()) return cycle;
        }
        path.remove(path.size() - 1);
        done.add(node);
        return List.of();
    }

    /** Package of the library (adapter.github, application, ...) -> packages of the library it uses. */
    private static Map<String, Set<String>> packageDependencies() throws IOException {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (Path file : sources()) {
            String from = packageOf(SOURCES.relativize(file).getParent().toString().replace('/', '.'));
            Set<String> uses = graph.computeIfAbsent(from, k -> new TreeSet<>());
            for (String reference : references(file)) {
                if (!reference.startsWith(LIBRARY)) continue;
                String to = packageOf(reference.substring(LIBRARY.length()));
                if (!to.isEmpty() && !to.equals(from)) uses.add(to);
            }
        }
        return graph;
    }

    /** "adapter.github.GitHubClient." -> "adapter.github"; "grading.GradeCalculator." -> "grading". */
    private static String packageOf(String qualified) {
        String[] parts = qualified.split("\\.");
        if (parts.length == 0 || parts[0].isEmpty() || !Character.isLowerCase(parts[0].charAt(0))) return "";
        if (parts[0].equals("adapter") && parts.length > 1 && Character.isLowerCase(parts[1].charAt(0))) return "adapter." + parts[1];
        return parts[0];
    }

    /** Ports and domain errors (capability core-ports, core-errors): only the JDK and the library itself. */
    @Test
    public void portsAndErrorsImportOnlyTheJdkAndTheLibrary() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sources()) {
            Path relative = SOURCES.relativize(file);
            if (!relative.startsWith("port") && !relative.startsWith("error")) continue;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Matcher m = IMPORT.matcher(line);
                if (m.matches() && !m.group(1).startsWith("java.") && !m.group(1).startsWith("br.com.gradechecker.core.")) {
                    violations.add(relative + ": " + m.group(1));
                }
            }
        }
        assertEquals("ports and errors depend on infrastructure", List.of(), violations);
    }

    @Test
    public void everyClassIsInACorePackage() throws IOException {
        List<Path> files = sources();
        assertTrue(files.size() > 20);
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(file.toString(), text.contains("package br.com.gradechecker.core."));
        }
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(f -> f.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    /** Package-qualified names used by the file (imports and inline), without comments and strings. */
    private static List<String> references(Path file) throws IOException {
        String code = Files.readString(file, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("'(?:\\\\.|[^'\\\\])'", "' '")
                .replaceAll("\"(?:\\\\.|[^\"\\\\\\n])*\"", "\"\"")
                .replaceAll("//[^\\n]*", " ");
        List<String> found = new ArrayList<>();
        Matcher m = REFERENCE.matcher(code);
        while (m.find()) found.add(m.group(1));
        return found;
    }
}
