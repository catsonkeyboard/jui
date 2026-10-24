package jui.testing;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs the tests in build/test-classes: every public void no-argument method
 * named test… of every class named …Test, each on a fresh instance.
 *
 * <p>Usage: {@code RunTests [Class | Class#method]}, where Class is a simple
 * or full name. Exits with 1 when a test fails or none is found.
 */
public final class RunTests {
    private RunTests() {}

    public static void main(String[] args) throws Exception {
        String filter = args.length > 0 ? args[0] : null;
        String classFilter = filter == null ? null : filter.split("#", 2)[0];
        String methodFilter = filter != null && filter.contains("#") ? filter.split("#", 2)[1] : null;

        int passed = 0, failed = 0;
        for (String name : testClasses(Path.of(System.getProperty("jui.testClasses", "build/test-classes")))) {
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if (classFilter != null && !classFilter.equals(simple) && !classFilter.equals(name)) {
                continue;
            }
            Class<?> cls = Class.forName(name);
            List<Method> tests = new ArrayList<>();
            for (Method m : cls.getMethods()) {
                if (m.getName().startsWith("test") && m.getParameterCount() == 0
                        && m.getReturnType() == void.class && !Modifier.isStatic(m.getModifiers())
                        && (methodFilter == null || methodFilter.equals(m.getName()))) {
                    tests.add(m);
                }
            }
            tests.sort(Comparator.comparing(Method::getName));
            for (Method m : tests) {
                String id = name + "#" + m.getName();
                try {
                    m.invoke(cls.getDeclaredConstructor().newInstance());
                    passed++;
                    System.out.println("PASS " + id);
                } catch (Throwable t) {
                    failed++;
                    Throwable cause = t instanceof InvocationTargetException i ? i.getCause() : t;
                    System.out.println("FAIL " + id + ": " + cause);
                    printWhere(cause, name);
                }
            }
        }
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0 || passed == 0) {
            System.exit(1);
        }
    }

    /** The names of the classes named …Test under dir. */
    private static List<String> testClasses(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.map(p -> dir.relativize(p).toString())
                    .filter(p -> p.endsWith("Test.class") && !p.contains("$"))
                    .map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.'))
                    .sorted()
                    .toList();
        }
    }

    /** Prints the frames of the stack in the test class, or the first frames. */
    private static void printWhere(Throwable t, String testClass) {
        StackTraceElement[] frames = t.getStackTrace();
        int shown = 0;
        for (StackTraceElement f : frames) {
            if (f.getClassName().startsWith(testClass)) {
                System.out.println("    at " + f);
                shown++;
            }
        }
        for (int i = 0; shown == 0 && i < Math.min(5, frames.length); i++) {
            System.out.println("    at " + frames[i]);
        }
    }
}
