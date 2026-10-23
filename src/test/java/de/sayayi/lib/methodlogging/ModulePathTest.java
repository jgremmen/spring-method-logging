/*
 * Copyright 2026 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.methodlogging;

import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Smoke test running a Spring application on the module path in a separate JVM.
 * <p>
 * The application module only requires {@code de.sayayi.lib.methodlogging} and uses Spring and message-format types
 * through the transitive dependencies of the library. The module path consists of the library classes and its runtime
 * classpath and is provided by the Gradle build in system property {@code methodlogging.modulePath}.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Module path")
class ModulePathTest
{
  private static final Map<String,String> SOURCES = Map.of(
      "module-info.java", """
          module test.app
          {
            requires de.sayayi.lib.methodlogging;

            opens test.app to spring.aop, spring.beans, spring.context, spring.core, de.sayayi.lib.methodlogging;
            opens test.closed to spring.aop, spring.beans, spring.context, spring.core;
          }
          """,
      "test/app/App.java", """
          package test.app;

          import de.sayayi.lib.message.MessageSupport;
          import de.sayayi.lib.methodlogging.MethodLogger;
          import de.sayayi.lib.methodlogging.MethodLoggerFactory;
          import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
          import de.sayayi.lib.methodlogging.Level;
          import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
          import org.springframework.context.annotation.AnnotationConfigApplicationContext;
          import org.springframework.context.annotation.Configuration;
          import org.springframework.core.ResolvableType;
          import test.closed.Closed;

          @Configuration
          @EnableMethodLogging
          public class App implements MethodLoggingConfigurer
          {
            @Override public MessageSupport messageSupport() { return null; }
            @Override public boolean excludeMethodParameter(ResolvableType type) { return false; }

            @Override
            public MethodLoggerFactory methodLoggerFactory()
            {
              return (field, obj, type) -> new MethodLogger() {
                @Override public void log(Level level, String message) {
                  System.out.println(level + "|" + (field == null ? null : field.getName()) + "|" + message);
                }
                @Override public boolean isLogEnabled(Level level) { return true; }
              };
            }

            public static void main(String[] args)
            {
              final var open = args[0].equals("open");
              final Class<?> beanClass = open ? Greeter.class : Closed.class;

              try(var context = new AnnotationConfigApplicationContext()) {
                context.register(App.class);
                context.registerBean(beanClass);
                context.refresh();

                if (open)
                  context.getBean(Greeter.class).greet("bob");
              } catch(Exception ex) {
                Throwable cause = ex;
                while(cause != null && !(cause instanceof IllegalStateException))
                  cause = cause.getCause();
                System.out.println("FAILED|" + (cause != null ? cause : ex).getMessage());
              }
            }
          }
          """,
      "test/app/Greeter.java", """
          package test.app;

          import de.sayayi.lib.methodlogging.annotation.MethodLogging;

          public class Greeter
          {
            private static final Object log = new Object();

            @MethodLogging
            public String greet(String who) {
              return "Hello " + who;
            }
          }
          """,
      "test/closed/Closed.java", """
          package test.closed;

          import de.sayayi.lib.methodlogging.annotation.MethodLogging;

          public class Closed
          {
            private static final Object log = new Object();

            @MethodLogging
            public void run() {
            }
          }
          """);

  @TempDir
  static Path tempDir;

  private static String modulePath;


  @BeforeAll
  static void compileApplication() throws Exception
  {
    val libraryModulePath = System.getProperty("methodlogging.modulePath");
    assumeTrue(libraryModulePath != null, "system property methodlogging.modulePath is set by the Gradle build");

    val sourceDir = tempDir.resolve("src");
    val classesDir = tempDir.resolve("classes");
    val arguments = new ArrayList<>(List.of(
        "-parameters", "--module-path", libraryModulePath, "-d", classesDir.toString()));

    for(var source: SOURCES.entrySet())
    {
      val sourceFile = sourceDir.resolve(source.getKey());

      Files.createDirectories(sourceFile.getParent());
      Files.writeString(sourceFile, source.getValue(), UTF_8);
      arguments.add(sourceFile.toString());
    }

    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)),
        "compilation of application module failed");

    modulePath = libraryModulePath + File.pathSeparator + classesDir;
  }


  @Test
  @DisplayName("Logger field in package opened to method logging")
  void openPackage() throws Exception
  {
    val output = run("open");

    assertEquals(3, output.size(), output::toString);
    assertTrue(output.get(0).matches("INFO\\|log\\|> greet\\(who=bob\\):\\d+"), output.get(0));
    assertEquals("DEBUG|log|result = Hello bob", output.get(1));
    assertTrue(output.get(2).matches("INFO\\|log\\|< greet:\\d+"), output.get(2));
  }


  @Test
  @DisplayName("Logger field in package not opened to method logging")
  void closedPackage() throws Exception
  {
    val output = run("closed");

    assertEquals(1, output.size(), output::toString);
    assertTrue(
        output.getFirst().startsWith(
            "FAILED|Logger field 'log' of class test.closed.Closed is not accessible; " +
            "package test.closed must be opened to module de.sayayi.lib.methodlogging"),
        output.getFirst());
  }


  private static @NotNull List<String> run(@NotNull String mode) throws Exception
  {
    val errorFile = tempDir.resolve(mode + ".err");
    val process = new ProcessBuilder(
        ProcessHandle.current().info().command().orElseThrow(),
        "--module-path", modulePath,
        // Spring 7 requires the commons logging module, which is not required by the automatic Spring modules
        "--add-modules", "ALL-MODULE-PATH",
        "-m", "test.app/test.app.App", mode)
        .redirectError(errorFile.toFile())
        .start();
    val output = new String(process.getInputStream().readAllBytes(), UTF_8).lines().toList();

    assertTrue(process.waitFor(60, SECONDS), "application did not terminate");
    assertEquals(0, process.exitValue(), () -> output + "\n" + readString(errorFile));

    return output;
  }


  private static @NotNull String readString(@NotNull Path file)
  {
    try {
      return Files.readString(file, UTF_8);
    } catch(Exception ex) {
      return ex.toString();
    }
  }
}
