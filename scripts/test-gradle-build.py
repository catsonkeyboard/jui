#!/usr/bin/env python3
"""Exercise the real build in an isolated checkout; never edit workspace sources."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class GradleBuildTest(unittest.TestCase):
    def test_incremental_build_and_existing_test_command(self):
        self.assertTrue((ROOT / "gradlew").is_file(), "Gradle Wrapper is missing")
        with tempfile.TemporaryDirectory(prefix="jui gradle ") as folder:
            project = Path(folder).resolve()
            for name in ("src", "gradle"):
                shutil.copytree(ROOT / name, project / name)
            for name in ("build.gradle", "settings.gradle", "gradle.properties", "gradlew", "build.sh"):
                shutil.copy2(ROOT / name, project / name)

            def run(*args, success=True):
                result = subprocess.run(args, cwd=project, text=True, stdout=subprocess.PIPE,
                                        stderr=subprocess.STDOUT, env=os.environ.copy())
                self.assertEqual(result.returncode == 0, success, result.stdout[-10000:])
                return result.stdout

            run("./build.sh", "test", "LongMapTest")
            output = run("./gradlew", "classes", "testClasses", "--console=plain", "--offline")
            for task in ("compileJava", "compileMetal", "processResources", "compileTestJava"):
                self.assertIn(f":{task} UP-TO-DATE", output)

            # A source edit must not rewrite unrelated class files.
            unrelated = project / "build/classes/java/main/jui/ui/engine/Node.class"
            timestamp = unrelated.stat().st_mtime_ns
            source = project / "src/main/java/jui/examples/CounterDemo.java"
            changed_class = project / "build/classes/java/main/jui/examples/CounterDemo.class"
            original_class = changed_class.read_bytes()
            source.write_text(source.read_text().replace("Integer.toString(n)", "Integer.toString(n + 100)"))
            output = run("./gradlew", "classes", "--console=plain", "--offline")
            self.assertNotIn(":compileJava UP-TO-DATE", output)
            self.assertEqual(unrelated.stat().st_mtime_ns, timestamp)
            self.assertNotEqual(changed_class.read_bytes(), original_class)
            self.assertIn(":compileMetal UP-TO-DATE", output)

            shader = project / "src/main/resources/jui/shaders/shader.metal"
            shader.write_text(shader.read_text() + "\n// shader input invalidation probe\n")
            output = run("./gradlew", "classes", "--console=plain", "--offline")
            self.assertNotIn(":compileMetal UP-TO-DATE", output)
            self.assertIn(":compileJava UP-TO-DATE", output)
            self.assertTrue((project / "build/resources/main/jui/shaders/shader.metallib").is_file())

            output = run("./gradlew", "classes", "-PmacosMin=15.0", "--console=plain", "--offline")
            self.assertNotIn(":compileMetal UP-TO-DATE", output)
            self.assertIn(":compileJava UP-TO-DATE", output)
            self.assertIn("1 passed, 0 failed", run("./build.sh", "test", "LongMapTest#testClear"))
            run("./build.sh", "test", "NoSuchTest", success=False)
            source = project / "src/test/java/jui/GradleFailureTest.java"
            source.write_text('package jui; public class GradleFailureTest { '
                              'public void testFailure() { throw new AssertionError("probe"); } }')
            self.assertIn("0 passed, 1 failed", run("./build.sh", "test", "GradleFailureTest", success=False))

            # Same dependency bytes at a new path must update the launch classpath.
            config = project / "build.gradle"
            config.write_text(config.read_text() + "\nif (providers.gradleProperty('probeJar').isPresent()) {\n"
                              "    dependencies { runtimeOnly files(providers.gradleProperty('probeJar').get()) }\n}\n")
            first_jar = project / "first.jar"
            with zipfile.ZipFile(first_jar, "w") as jar:
                jar.writestr("probe.txt", "same contents at different paths")
            run("./gradlew", "runtimeClasspath", "-PprobeJar=first.jar", "--offline")
            second_jar = project / "second.jar"
            first_jar.rename(second_jar)
            run("./gradlew", "runtimeClasspath", "-PprobeJar=second.jar", "--offline")
            classpath = (project / "build/runtime-classpath.txt").read_text()
            self.assertIn(str(second_jar), classpath)
            self.assertNotIn(str(first_jar), classpath)

            if os.environ.get("JUI_TEST_NATIVE") == "1":
                # Start without build/: catches shell redirection before Gradle creates directories.
                shutil.rmtree(project / "build")
                run("./build.sh", "native-demo", "Notes", "--quit-after-first-frame")
                run("./build.sh", "native-demo", "NotesMotion", "--quit-after-first-frame")
                self.assertIn(":native_build_notes_demo UP-TO-DATE",
                              (project / "build/notes-demo-build.log").read_text())
                native = ("./gradlew", "nativeCompile", "-PnativeMain=jui.examples.NotesDemo",
                          "-PnativeOutput=build/notes-demo",
                          '-PnativeArgsJson=["-R:MaxHeapSize=34603008"]', "--console=plain", "--offline")
                self.assertNotIn(":native_build_notes_demo UP-TO-DATE", run(*native))
                self.assertIn(":native_build_notes_demo UP-TO-DATE", run(*native))


if __name__ == "__main__":
    unittest.main()
