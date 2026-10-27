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
package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodClassKey;
import org.springframework.core.ResolvableType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Method definition cache")
class MethodDefinitionCacheTest
{
  @Test
  @DisplayName("Methods without method logging are cached")
  void notAnnotated() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {}, true);
    final var method = Bean.class.getMethod("notLogged");

    assertNull(source.getMethodDefinition(method, Bean.class));
    assertEquals(
        Optional.empty(),
        source.methodLoggingDefinitionCache.get(new MethodClassKey(method, Bean.class)));
    assertNull(source.getMethodDefinition(method, Bean.class));
    assertEquals(1, source.methodLoggingDefinitionCache.size());
  }


  @Test
  @DisplayName("Methods with method logging are cached")
  void annotated() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {}, true);
    final var method = Bean.class.getMethod("logged", String.class);
    final var methodDef = source.getMethodDefinition(method, Bean.class);

    assertNotNull(methodDef);
    assertSame(methodDef, source.getMethodDefinition(method, Bean.class));
    assertEquals(1, source.methodLoggingDefinitionCache.size());
  }


  @Test
  @DisplayName("Configurer may request method definitions during analysis")
  void reentrantAnalysis() throws Exception
  {
    final var otherMethod = Bean.class.getMethod("notLogged");
    final var source = new AnnotationMethodLoggingSource[1];

    source[0] = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType) {
            // e.g. a configurer delegating to a bean with method logging
            return source[0].getMethodDefinition(otherMethod, Bean.class) != null;
          }
        }, true);

    assertNotNull(source[0].getMethodDefinition(Bean.class.getMethod("logged", String.class), Bean.class));
    assertEquals(2, source[0].methodLoggingDefinitionCache.size());
  }




  @Test
  @DisplayName("Interface method and implementation share the definition")
  void sharedDefinition() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(() -> new MethodLoggingConfigurer() {}, true);

    final var methodDef = source.getMethodDefinition(Service.class.getMethod("logged", String.class), Bean.class);

    assertNotNull(methodDef);
    assertSame(methodDef, source.getMethodDefinition(Bean.class.getMethod("logged", String.class), Bean.class));
  }


  @Test
  @DisplayName("Interface method and implementation share the failed analysis")
  void sharedFailure() throws Exception
  {
    final var analyses = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            analyses.incrementAndGet();
            return false;
          }
        }, true);
    final var interfaceMethod = Service.class.getMethod("failing", String.class);
    final var method = Bean.class.getMethod("failing", String.class);

    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> source.getMethodDefinition(interfaceMethod, Bean.class));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class)));
    assertEquals(1, analyses.get());

    // reported once
    assertTrue(source.isFirstFailureReport(method, Bean.class));
    assertFalse(source.isFirstFailureReport(interfaceMethod, Bean.class));
  }


  @Test
  @DisplayName("Class file is read once per declaring class")
  void classLocationCached() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(() -> new MethodLoggingConfigurer() {}, true);

    final var logged = source.getMethodDefinition(Bean.class.getMethod("logged", String.class), Bean.class);
    final var other = source.getMethodDefinition(Bean.class.getMethod("otherLogged"), Bean.class);

    assertNotNull(logged);
    assertNotNull(other);
    assertEquals(1, source.classLocationCache.size());
    assertTrue(logged.location.getLineNumber() > 0);
    assertTrue(other.location.getLineNumber() > logged.location.getLineNumber());
    assertEquals("MethodDefinitionCacheTest.java", other.location.getFileName());
  }


  @Test
  @DisplayName("Class file of a bootstrap class is read")
  void classLocationBootstrapClass()
  {
    final var classLocation = ClassLocation.read(String.class);

    assertEquals("String.java", classLocation.sourceFile());
    assertTrue(classLocation.lineNumbers().get("length()I") > 0);
  }


  @Test
  @DisplayName("Class without class file has no location")
  void classLocationNoClassFile()
  {
    final Runnable lambda = () -> {};
    final var classLocation = ClassLocation.read(lambda.getClass());

    assertNull(classLocation.sourceFile());
    assertTrue(classLocation.lineNumbers().isEmpty());
  }


  @Test
  @DisplayName("Failed analysis is cached and its failure passed on")
  void failedAnalysis() throws Exception
  {
    final var analyses = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            analyses.incrementAndGet();
            return false;
          }
        }, true);
    final var method = Bean.class.getMethod("failing", String.class);

    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> source.getMethodDefinition(method, Bean.class));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class)));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.hasMethodLogging(method, Bean.class)));
    assertEquals(1, analyses.get());
    assertTrue(source.methodLoggingDefinitionCache.isEmpty());

    // reported once
    assertTrue(source.isFirstFailureReport(method, Bean.class));
    assertFalse(source.isFirstFailureReport(method, Bean.class));
  }


  @ParameterizedTest(name = "resolveSpecificMethod = {0}")
  @ValueSource(booleans = { true, false })
  @DisplayName("Error raised by the analysis is cached and passed on as IllegalStateException")
  void failedAnalysisWithError(boolean resolveSpecificMethod) throws Exception
  {
    final var analyses = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            analyses.incrementAndGet();
            throw new NoClassDefFoundError("com/example/Missing");
          }
        },
        resolveSpecificMethod);
    final var method = Bean.class.getMethod("logged", String.class);

    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> source.getMethodDefinition(method, Bean.class));
    assertInstanceOf(NoClassDefFoundError.class, ex.getCause());
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class)));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.hasMethodLogging(method, Bean.class)));
    assertEquals(1, analyses.get());

    // reported once
    assertTrue(source.isFirstFailureReport(method, Bean.class));
    assertFalse(source.isFirstFailureReport(method, Bean.class));
  }


  @Test
  @DisplayName("Virtual machine error raised by the analysis is passed on and not cached")
  void failedAnalysisWithVirtualMachineError() throws Exception
  {
    final var analyses = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            analyses.incrementAndGet();
            throw new OutOfMemoryError("simulated");
          }
        }, true);
    final var method = Bean.class.getMethod("logged", String.class);

    assertThrowsExactly(OutOfMemoryError.class, () -> source.getMethodDefinition(method, Bean.class));
    assertThrowsExactly(OutOfMemoryError.class, () -> source.getMethodDefinition(method, Bean.class));
    assertEquals(2, analyses.get());
  }


  @Test
  @DisplayName("Error raised by a configurer method is cached and passed on as IllegalStateException")
  void failingConfigurerWithError() throws Exception
  {
    final var invocations = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public @NotNull Level defaultEntryExitLevel()
          {
            invocations.incrementAndGet();
            throw new NoClassDefFoundError("com/example/Missing");
          }
        }, true);
    final var method = Bean.class.getMethod("logged", String.class);

    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> source.getMethodDefinition(method, Bean.class));
    assertInstanceOf(NoClassDefFoundError.class, ex.getCause());
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class)));
    assertEquals(1, invocations.get());
  }


  @Test
  @DisplayName("Failing configurer methods are invoked once")
  void failingConfigurer() throws Exception
  {
    final var invocations = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public @NotNull Level defaultEntryExitLevel()
          {
            invocations.incrementAndGet();
            return Level.DEFAULT;
          }
        }, true);
    final var logged = Bean.class.getMethod("logged", String.class);
    final var failing = Bean.class.getMethod("failing", String.class);

    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> source.getMethodDefinition(logged, Bean.class));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(logged, Bean.class)));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(failing, Bean.class)));
    assertEquals(1, invocations.get());
  }


  @Test
  @DisplayName("Failure obtaining the configurer bean is not cached")
  void unavailableConfigurer() throws Exception
  {
    final var available = new AtomicBoolean();
    final var source = new AnnotationMethodLoggingSource(
        () -> {
          if (!available.get())
            throw new IllegalStateException("configurer bean currently in creation");

          return new MethodLoggingConfigurer() {};
        }, true);
    final var method = Bean.class.getMethod("logged", String.class);

    assertThrowsExactly(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class));
    assertTrue(source.isFirstFailureReport(method, Bean.class));
    assertTrue(source.isFirstFailureReport(method, Bean.class));

    available.set(true);
    assertNotNull(source.getMethodDefinition(method, Bean.class));
  }


  @Test
  @DisplayName("Failure obtaining the configurer bean is cached once all singletons exist")
  void failingConfigurerAfterSingletonsInstantiated() throws Exception
  {
    final var invocations = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> {
          invocations.incrementAndGet();
          throw new IllegalStateException("no unique configurer bean");
        }, true);
    final var method = Bean.class.getMethod("logged", String.class);

    final var ex = assertThrowsExactly(IllegalStateException.class, source::singletonsInstantiated);
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class)));
    assertSame(ex, assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(method, Bean.class)));
    assertEquals(1, invocations.get());

    // reported once
    assertTrue(source.isFirstFailureReport(method, Bean.class));
    assertFalse(source.isFirstFailureReport(method, Bean.class));
  }




  public interface Service
  {
    void logged(String value);


    void failing(String value);
  }




  public static class Bean implements Service
  {
    @Override
    @MethodLogging
    public void logged(@SuppressWarnings("unused") String value) {
    }


    @Override
    @MethodLogging(exclude = "missing")
    public void failing(@SuppressWarnings("unused") String value) {
    }


    @MethodLogging
    public int otherLogged() {
      return 0;
    }


    @SuppressWarnings("EmptyMethod")
    public void notLogged() {
    }
  }
}
