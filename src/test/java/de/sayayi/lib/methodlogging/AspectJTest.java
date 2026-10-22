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

import de.sayayi.lib.methodlogging.MethodLoggingTest.ListMethodLoggerFactory;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.aspectj.MethodLoggingAspect;
import lombok.val;
import org.aspectj.bridge.IMessage;
import org.aspectj.bridge.MessageHandler;
import org.aspectj.lang.Aspects;
import org.aspectj.tools.ajc.Main;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.context.annotation.AdviceMode.ASPECTJ;


/**
 * Tests AspectJ based method logging ({@code @EnableMethodLogging(mode = ASPECTJ)}) with compile-time and load-time
 * weaving.
 *
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
@DisplayName("AspectJ mode")
class AspectJTest
{
  private static final ListMethodLoggerFactory FACTORY = new ListMethodLoggerFactory();

  private static final List<String> EXPECTED_LOG = List.of(
      "INFO|> outer(who=bob)",
      "INFO|> inner",
      "INFO|< inner",
      "INFO|< outer");

  private static final String SERVICE_SOURCE = """
      package ctw;

      import de.sayayi.lib.methodlogging.annotation.MethodLogging;

      import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;

      public class Service
      {
        @MethodLogging(lineNumber = HIDE, result = HIDE)
        public void outer(String who) {
          inner();
        }

        @MethodLogging(lineNumber = HIDE)
        private void inner() {
        }
      }
      """;

  @TempDir
  private Path tempDir;


  @BeforeEach
  void setup()
  {
    FACTORY.log.clear();
    FACTORY.fields.clear();
  }


  @AfterEach
  void cleanup() {
    // the aspect is a singleton; do not leak the interceptor of the closed context to other tests
    Aspects.aspectOf(MethodLoggingAspect.class).setMethodInterceptor(null);
  }


  @Test
  @DisplayName("Compile-time weaving with library on aspect path")
  void compileTimeWeaving() throws Exception
  {
    val sourceFile = tempDir.resolve("src/ctw/Service.java");
    val classesDir = createDirectories(tempDir.resolve("classes"));
    val libraryClasses = new File(MethodLoggingAspect.class.getProtectionDomain().getCodeSource().getLocation().toURI());

    createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, SERVICE_SOURCE, UTF_8);

    val messages = new MessageHandler();
    new Main().run(new String[] {
        "-21", "-parameters",
        "-aspectpath", libraryClasses.toString(),
        "-classpath", System.getProperty("java.class.path"),
        "-d", classesDir.toString(),
        sourceFile.toString()
    }, messages);
    assertEquals(0, messages.numMessages(IMessage.ERROR, true),
        () -> Arrays.toString(messages.getMessages(IMessage.ERROR, true)));

    try(val classLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, getClass().getClassLoader());
        val context = new AnnotationConfigApplicationContext()) {
      val serviceClass = classLoader.loadClass("ctw.Service");

      context.setClassLoader(classLoader);
      context.register(AspectJConfig.class);
      context.registerBean(serviceClass);
      context.refresh();

      val service = context.getBean(serviceClass);
      assertFalse(AopUtils.isAopProxy(service));

      serviceClass.getMethod("outer", String.class).invoke(service, "bob");
      assertEquals(EXPECTED_LOG, FACTORY.log);

      // AspectJ also applies to instances not managed by Spring
      FACTORY.log.clear();
      serviceClass.getMethod("outer", String.class).invoke(serviceClass.getConstructor().newInstance(), "bob");
      assertEquals(EXPECTED_LOG, FACTORY.log);
    }
  }


  @Test
  @DisplayName("Load-time weaving with aop.xml provided by library")
  void loadTimeWeaving() throws Exception
  {
    val classpath = System.getProperty("java.class.path");
    val weaverAgent = Arrays.stream(classpath.split(File.pathSeparator))
        .filter(entry -> new File(entry).getName().startsWith("aspectjweaver-"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("aspectjweaver not found on classpath"));
    val errorFile = tempDir.resolve("ltw.err");

    val process = new ProcessBuilder(
        ProcessHandle.current().info().command().orElseThrow(),
        "-javaagent:" + weaverAgent,
        "-classpath", classpath,
        LoadTimeWeavingApplication.class.getName())
        .redirectError(errorFile.toFile())
        .start();
    val output = new String(process.getInputStream().readAllBytes(), UTF_8).lines().toList();

    assertTrue(process.waitFor(60, SECONDS), "application did not terminate");
    assertEquals(0, process.exitValue(), () -> output + "\n" + readString(errorFile));
    assertEquals(List.of("proxy=false", "INFO|> outer(who=bob)", "INFO|> inner", "INFO|< inner", "INFO|< outer"),
        output, () -> readString(errorFile));
  }


  @Test
  @DisplayName("Without weaving no proxies are created and nothing is logged")
  void noWeaving()
  {
    try(val context = new AnnotationConfigApplicationContext()) {
      context.register(AspectJConfig.class);
      context.registerBean(Service.class);
      context.refresh();

      val service = context.getBean(Service.class);
      assertFalse(AopUtils.isAopProxy(service));

      service.outer("bob");
      assertTrue(FACTORY.log.isEmpty(), FACTORY.log::toString);
    }
  }


  private static @NotNull String readString(@NotNull Path file)
  {
    try {
      return Files.readString(file, UTF_8);
    } catch(Exception ex) {
      return ex.toString();
    }
  }




  public static class Service
  {
    @MethodLogging(lineNumber = HIDE, result = HIDE)
    public void outer(@SuppressWarnings("unused") String who) {
      inner();
    }


    @MethodLogging(lineNumber = HIDE)
    private void inner() {
    }
  }




  @Configuration
  @EnableMethodLogging(mode = ASPECTJ)
  static class AspectJConfig implements MethodLoggingConfigurer
  {
    @Override
    public MethodLoggerFactory methodLoggerFactory() {
      return FACTORY;
    }
  }




  /**
   * Application started in a separate JVM with the AspectJ weaver agent. The {@link Service} class is woven when it
   * is loaded.
   */
  public static final class LoadTimeWeavingApplication
  {
    public static void main(String[] args)
    {
      try(val context = new AnnotationConfigApplicationContext(Config.class)) {
        val service = context.getBean(Service.class);

        System.out.println("proxy=" + AopUtils.isAopProxy(service));
        service.outer("bob");
      }
    }


    @Configuration
    @EnableMethodLogging(mode = ASPECTJ)
    static class Config implements MethodLoggingConfigurer
    {
      @Bean
      public Service service() {
        return new Service();
      }


      @Override
      public MethodLoggerFactory methodLoggerFactory()
      {
        return (loggerField, obj, type) -> new MethodLogger() {
          @Override
          public void log(@NotNull Level level, String message) {
            System.out.println(level + "|" + message);
          }

          @Override
          public boolean isLogEnabled(@NotNull Level level) {
            return true;
          }
        };
      }
    }
  }
}
