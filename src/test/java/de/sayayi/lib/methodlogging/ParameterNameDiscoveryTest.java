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
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.DefaultParameterNameDiscoverer;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static javax.tools.ToolProvider.getSystemJavaCompiler;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.util.ClassUtils.isPresent;


/**
 * Tests the parameter name discovery for logged method parameters.
 * <p>
 * Parameter names are resolved from {@code @ParamLog(name)}, the parameter name discoverer (the
 * {@code MethodParameters} attribute created by {@code javac -parameters} or the local variable table created by
 * {@code javac -g}) or the synthesized reflection names {@code arg0}, {@code arg1}, ... As the test sources are
 * compiled with {@code -parameters} and full debug information, the classes under test are compiled at runtime with the
 * compiler options required for each variant.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Parameter name discovery")
class ParameterNameDiscoveryTest
{
  private static final ListMethodLoggerFactory FACTORY = new ListMethodLoggerFactory();

  private static final String CLASS_SOURCE = """
      package test;

      import de.sayayi.lib.methodlogging.annotation.MethodLogging;
      import de.sayayi.lib.methodlogging.annotation.ParamLog;

      import static de.sayayi.lib.methodlogging.Visibility.HIDE;

      public class Bean
      {
        @MethodLogging(lineNumber = HIDE, result = HIDE)
        public void call(int id, String name, @ParamLog(name = "renamed") boolean flag) {
        }
      }
      """;

  private static final String IMPLEMENTATION_SOURCE = """
      package test;

      import de.sayayi.lib.methodlogging.annotation.MethodLogging;
      import de.sayayi.lib.methodlogging.annotation.ParamLog;

      import static de.sayayi.lib.methodlogging.Visibility.HIDE;

      public class Impl implements If
      {
        @Override
        @MethodLogging(lineNumber = HIDE, result = HIDE)
        public void call(int id, String name, @ParamLog(name = "renamed") boolean flag) {
        }
      }
      """;

  private static final String INTERFACE_SOURCE = """
      package test;

      public interface If {
        void call(int a, String b, boolean c);
      }
      """;

  @TempDir
  private Path tempDir;

  private Path classesDir;
  private URLClassLoader classLoader;


  @BeforeEach
  void setup() throws Exception
  {
    FACTORY.log.clear();
    FACTORY.fields.clear();

    classesDir = Files.createDirectories(tempDir.resolve("classes"));
    classLoader = new URLClassLoader(new URL[] { classesDir.toUri().toURL() }, getClass().getClassLoader());
  }


  @AfterEach
  void cleanup() throws Exception {
    classLoader.close();
  }


  /** Spring 6.1 removed the parameter name discovery from the local variable table. */
  private static final boolean LOCAL_VARIABLE_TABLE_DISCOVERY =
      isPresent("org.springframework.core.LocalVariableTableParameterNameDiscoverer", null);

  private static final String SYNTHESIZED_NAMES_ENTRY = "INFO|> call(arg0=1,arg1=x,renamed=true)";


  @ParameterizedTest(name = "{0}", quoteTextArguments = false)
  @CsvSource({
      "METHOD_PARAMETERS,    'INFO|> call(id=1,name=x,renamed=true)'",
      "LOCAL_VARIABLE_TABLE, 'INFO|> call(id=1,name=x,renamed=true)'",
      "NONE,                 'INFO|> call(arg0=1,arg1=x,renamed=true)'"
  })
  @DisplayName("Class")
  void className(@NotNull NameInfo nameInfo, @NotNull String expectedEntry) throws Exception
  {
    compile("test.Bean", CLASS_SOURCE, nameInfo);

    // make sure the class provides exactly the expected parameter name information
    val call = callMethod("test.Bean");
    val discoveredNames = new DefaultParameterNameDiscoverer().getParameterNames(call);

    switch(nameInfo)
    {
      case METHOD_PARAMETERS -> assertTrue(stream(call.getParameters()).allMatch(Parameter::isNamePresent));
      case LOCAL_VARIABLE_TABLE -> {
        assertTrue(stream(call.getParameters()).noneMatch(Parameter::isNamePresent));

        if (LOCAL_VARIABLE_TABLE_DISCOVERY)
          assertNotNull(discoveredNames);
        else
        {
          assertNull(discoveredNames);
          expectedEntry = SYNTHESIZED_NAMES_ENTRY;
        }
      }
      case NONE -> assertNull(discoveredNames);
    }

    invoke("test.Bean", "test.Bean", 1, "x", true);

    assertEquals(expectedEntry, FACTORY.log.getFirst());
  }


  @Test
  @DisplayName("Excluding parameters by synthesized name")
  void excludeSynthesizedName() throws Exception
  {
    compile("test.Bean", """
        package test;

        import de.sayayi.lib.methodlogging.annotation.MethodLogging;

        import static de.sayayi.lib.methodlogging.Visibility.HIDE;

        public class Bean
        {
          @MethodLogging(lineNumber = HIDE, result = HIDE, exclude = "arg1")
          public void call(int id, String name, boolean flag) {
          }
        }
        """, NameInfo.NONE);

    invoke("test.Bean", "test.Bean", 1, "x", true);

    assertEquals("INFO|> call(arg0=1,arg2=true)", FACTORY.log.getFirst());
  }


  @ParameterizedTest(name = "{0}", quoteTextArguments = false)
  @CsvSource({
      "METHOD_PARAMETERS, other, false",
      "NONE,              name,  true"
  })
  @DisplayName("Excluding a name matching no parameter fails")
  void excludeUnmatchedName(@NotNull NameInfo nameInfo, @NotNull String unmatchedName, boolean namesUnavailable)
      throws Exception
  {
    compile("test.Bean", """
        package test;

        import de.sayayi.lib.methodlogging.annotation.MethodLogging;

        import static de.sayayi.lib.methodlogging.Visibility.HIDE;

        public class Bean
        {
          @MethodLogging(lineNumber = HIDE, result = HIDE, exclude = { "name", "other" })
          public void call(int id, String name, boolean flag) {
          }
        }
        """, nameInfo);

    val ex = assertThrows(IllegalStateException.class,
        () -> invoke("test.Bean", "test.Bean", 1, "x", true));

    assertTrue(ex.getMessage().startsWith("Excluded parameter '" + unmatchedName + "' matches no parameter of "),
        ex.getMessage());
    assertEquals(namesUnavailable, ex.getMessage().contains("parameter names are not available"), ex.getMessage());
    assertTrue(FACTORY.log.isEmpty());
  }


  @ParameterizedTest(name = "{0}", quoteTextArguments = false)
  @CsvSource({
      "METHOD_PARAMETERS, 'INFO|> call(id=1,flag=true)'",
      "NONE,              ''"
  })
  @DisplayName("Excluding a name matching no parameter by composed annotation fails without parameter names only")
  void excludeUnmatchedNameByComposedAnnotation(@NotNull NameInfo nameInfo, @NotNull String expectedEntry)
      throws Exception
  {
    compile("test.Audited", """
        package test;

        import de.sayayi.lib.methodlogging.annotation.MethodLogging;

        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;

        import static de.sayayi.lib.methodlogging.Visibility.HIDE;
        import static java.lang.annotation.ElementType.METHOD;
        import static java.lang.annotation.RetentionPolicy.RUNTIME;

        @Target(METHOD)
        @Retention(RUNTIME)
        @MethodLogging(lineNumber = HIDE, result = HIDE, exclude = { "name", "password" })
        public @interface Audited {
        }
        """, NameInfo.NONE);
    compile("test.Bean", """
        package test;

        public class Bean
        {
          @Audited
          public void call(int id, String name, boolean flag) {
          }
        }
        """, nameInfo);

    if (!expectedEntry.isEmpty())
    {
      // with parameter names, a composed annotation may exclude names not present on every method
      invoke("test.Bean", "test.Bean", 1, "x", true);
      assertEquals(expectedEntry, FACTORY.log.getFirst());
    }
    else
    {
      val ex = assertThrows(IllegalStateException.class,
          () -> invoke("test.Bean", "test.Bean", 1, "x", true));

      assertTrue(ex.getMessage().startsWith("Excluded parameter 'name' matches no parameter of "), ex.getMessage());
      assertTrue(FACTORY.log.isEmpty());
    }
  }


  @ParameterizedTest(name = "{0}", quoteTextArguments = false)
  @CsvSource({ "secret", "name" })
  @DisplayName("Excluding parameters by interface or implementation name")
  void excludeInterfaceOrImplementationName(@NotNull String excludedName) throws Exception
  {
    compile("test.If", """
        package test;

        public interface If {
          void call(int id, String secret, boolean flag);
        }
        """, NameInfo.METHOD_PARAMETERS);
    compile("test.Impl", """
        package test;

        import de.sayayi.lib.methodlogging.annotation.MethodLogging;

        import static de.sayayi.lib.methodlogging.Visibility.HIDE;

        public class Impl implements If
        {
          @Override
          @MethodLogging(lineNumber = HIDE, result = HIDE, exclude = "%s")
          public void call(int id, String name, boolean flag) {
          }
        }
        """.formatted(excludedName), NameInfo.METHOD_PARAMETERS);

    invoke("test.Impl", "test.If", 1, "x", true);

    assertEquals("INFO|> call(id=1,flag=true)", FACTORY.log.getFirst());
  }


  @Test
  @DisplayName("Excluding parameters by @ParamLog name without parameter names")
  void excludeParamLogName() throws Exception
  {
    compile("test.Bean", """
        package test;

        import de.sayayi.lib.methodlogging.annotation.MethodLogging;
        import de.sayayi.lib.methodlogging.annotation.ParamLog;

        import static de.sayayi.lib.methodlogging.Visibility.HIDE;

        public class Bean
        {
          @MethodLogging(lineNumber = HIDE, result = HIDE, exclude = "name")
          public void call(int id, @ParamLog(name = "name") String name, boolean flag) {
          }
        }
        """, NameInfo.NONE);

    invoke("test.Bean", "test.Bean", 1, "x", true);

    assertEquals("INFO|> call(arg0=1,arg2=true)", FACTORY.log.getFirst());
  }


  @ParameterizedTest(name = "interface {0}, implementation {1}", quoteTextArguments = false)
  @CsvSource({
      // implementation names are preferred over interface names
      "METHOD_PARAMETERS,    METHOD_PARAMETERS,    'INFO|> call(id=1,name=x,renamed=true)'",
      "NONE,                 METHOD_PARAMETERS,    'INFO|> call(id=1,name=x,renamed=true)'",
      "NONE,                 LOCAL_VARIABLE_TABLE, 'INFO|> call(id=1,name=x,renamed=true)'",
      // interface names are used if the implementation provides none
      "METHOD_PARAMETERS,    NONE,                 'INFO|> call(a=1,b=x,renamed=true)'",
      // abstract methods have no code and therefore no local variable table
      "LOCAL_VARIABLE_TABLE, NONE,                 'INFO|> call(arg0=1,arg1=x,renamed=true)'",
      "NONE,                 NONE,                 'INFO|> call(arg0=1,arg1=x,renamed=true)'"
  })
  @DisplayName("JDK proxy")
  void proxyNames(@NotNull NameInfo interfaceNameInfo, @NotNull NameInfo implementationNameInfo,
                  @NotNull String expectedEntry) throws Exception
  {
    compile("test.If", INTERFACE_SOURCE, interfaceNameInfo);
    compile("test.Impl", IMPLEMENTATION_SOURCE, implementationNameInfo);

    // without local variable table discovery, the implementation provides no names
    if (!LOCAL_VARIABLE_TABLE_DISCOVERY && interfaceNameInfo == NameInfo.NONE &&
        implementationNameInfo == NameInfo.LOCAL_VARIABLE_TABLE)
      expectedEntry = SYNTHESIZED_NAMES_ENTRY;

    invoke("test.Impl", "test.If", 1, "x", true);

    assertEquals(expectedEntry, FACTORY.log.getFirst());
  }


  @Test
  @DisplayName("JDK proxy: @ParamLog name on interface is used")
  void proxyInterfaceParamLogName() throws Exception
  {
    compile("test.If", """
        package test;

        import de.sayayi.lib.methodlogging.annotation.ParamLog;

        public interface If {
          void call(@ParamLog(name = "ifId") int id, String name, boolean flag);
        }
        """, NameInfo.NONE);
    compile("test.Impl", IMPLEMENTATION_SOURCE, NameInfo.NONE);

    invoke("test.Impl", "test.If", 1, "x", true);

    assertEquals("INFO|> call(ifId=1,arg1=x,renamed=true)", FACTORY.log.getFirst());
  }


  private void compile(@NotNull String className, @NotNull String source, @NotNull NameInfo nameInfo)
      throws Exception
  {
    val sourceFile = tempDir
        .resolve("src").resolve(className.replace('.', '/') + ".java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, source, UTF_8);

    val arguments = new ArrayList<>(List.of(nameInfo.compilerOptions));
    arguments.addAll(List.of(
        "-classpath", System.getProperty("java.class.path") + File.pathSeparator + classesDir,
        "-d", classesDir.toString(),
        sourceFile.toString()));

    assertEquals(0, getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)),
        "compilation of " + className + " failed");
  }


  private @NotNull Method callMethod(@NotNull String className) throws Exception {
    return classLoader.loadClass(className).getMethod("call", int.class, String.class, boolean.class);
  }


  @SuppressWarnings("SameParameterValue")
  private void invoke(@NotNull String beanClassName, @NotNull String beanTypeName, Object... args) throws Exception
  {
    try(val context = new AnnotationConfigApplicationContext()) {
      val beanClass = classLoader.loadClass(beanClassName);
      val beanType = classLoader.loadClass(beanTypeName);

      context.setClassLoader(classLoader);
      context.register(Config.class);
      context.registerBean(beanClass);
      context.refresh();

      val bean = context.getBean(beanType);
      if (beanType.isInterface())
        assertTrue(Proxy.isProxyClass(bean.getClass()));

      callMethod(beanTypeName).invoke(bean, args);
    }
  }




  /** Parameter name information compiled into a class. */
  enum NameInfo
  {
    /** {@code MethodParameters} attribute, available through reflection. */
    METHOD_PARAMETERS("-parameters", "-g:none"),

    /** Local variable table, which is part of the debug information. */
    LOCAL_VARIABLE_TABLE("-g"),

    /** No parameter name information. */
    NONE("-g:none");


    private final String[] compilerOptions;


    NameInfo(String... compilerOptions) {
      this.compilerOptions = compilerOptions;
    }
  }




  @Configuration
  @EnableMethodLogging
  static class Config implements MethodLoggingConfigurer
  {
    @Override
    public MethodLoggerFactory methodLoggerFactory() {
      return FACTORY;
    }
  }
}
