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
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.logger.JULLoggerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Method logging interceptor")
class MethodLoggingInterceptorTest
{
  @Test
  @DisplayName("Method without method logging definition is invoked")
  void noMethodDefinition()
  {
    final var interceptor = new MethodLoggingInterceptor(
        new AnnotationMethodLoggingSource(() -> new MethodLoggingConfigurer() {}, true));

    // interceptor applied without pointcut, as in a manually built proxy
    final var proxyFactory = new ProxyFactory((Supplier<String>)() -> "result");
    proxyFactory.addAdvice(interceptor);

    @SuppressWarnings("unchecked")
    final var proxy = (Supplier<String>)proxyFactory.getProxy();

    assertEquals("result", proxy.get());
  }


  @Test
  @DisplayName("Invocation without target is passed on")
  void noTarget()
  {
    final var interceptor = new MethodLoggingInterceptor(
        new AnnotationMethodLoggingSource(() -> new MethodLoggingConfigurer() {}, true));

    // proxy without target, as with an EmptyTargetSource; the last advice provides the result
    final var proxyFactory = new ProxyFactory();
    proxyFactory.addInterface(Supplier.class);
    proxyFactory.addAdvice(interceptor);
    proxyFactory.addAdvice((MethodInterceptor)invocation -> "result");

    @SuppressWarnings("unchecked")
    final var proxy = (Supplier<String>)proxyFactory.getProxy();

    assertEquals("result", proxy.get());
  }


  @Test
  @DisplayName("Method is invoked if the method analysis fails")
  void failingAnalysis() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public MethodLoggerFactory methodLoggerFactory() {
            return JULLoggerFactory.strict();
          }
        },
        true);

    final var method = NoLoggerField.class.getMethod("get");

    // proxy mode fails when analyzing the method, as the bean has no logger field...
    assertThrowsExactly(IllegalStateException.class, () -> source.getMethodDefinition(method, NoLoggerField.class));

    // ...while AspectJ mode analyzes on invocation, which must not fail
    final var proxyFactory = new ProxyFactory(new NoLoggerField());
    proxyFactory.addAdvice(new MethodLoggingInterceptor(source));

    @SuppressWarnings("unchecked")
    final var proxy = (Supplier<String>)proxyFactory.getProxy();

    assertEquals("result", proxy.get());
    assertEquals("result", proxy.get());

    // the cached failure has been reported by the first invocation (of the interface method invoked by the proxy)
    assertFalse(source.isFirstFailureReport(Supplier.class.getMethod("get"), NoLoggerField.class));
  }


  @Test
  @DisplayName("Exception is passed on and logged on exit")
  void exception()
  {
    final var log = new ArrayList<String>();
    final var bean = proxy(new Bean(), (loggerField, obj, type) -> listLogger(log));

    assertEquals("314", assertThrowsExactly(IllegalArgumentException.class, () -> bean.exception(314)).getMessage());
    assertEquals(List.of("INFO|> exception(id=314)", "INFO|< exception -> IllegalArgumentException(314)"), log);
  }


  @Test
  @DisplayName("Failing parameter/result formatting does not affect method invocation")
  void formattingFailure()
  {
    final var log = new ArrayList<String>();
    final var bean = proxy(new Bean(), (loggerField, obj, type) -> listLogger(log));
    final var value = new Unprintable();

    assertSame(value, bean.unprintable(value));
    assertEquals(
        List.of(
            "INFO|> unprintable(value=<format failed: IllegalStateException>)",
            "DEBUG|result = <format failed: IllegalStateException>",
            "INFO|< unprintable"),
        log);
  }


  @Test
  @DisplayName("Stack overflow in parameter/result formatting does not affect method invocation")
  void formattingStackOverflow()
  {
    final var log = new ArrayList<String>();
    final var bean = proxy(new Bean(), (loggerField, obj, type) -> listLogger(log));
    final var value = new Cyclic();

    assertSame(value, bean.cyclic(value));
    assertEquals(
        List.of(
            "INFO|> cyclic(value=<format failed: StackOverflowError>)",
            "DEBUG|result = <format failed: StackOverflowError>",
            "INFO|< cyclic"),
        log);
  }


  @Test
  @DisplayName("Method logger raising an error does not affect method invocation")
  void loggerError()
  {
    final var bean = proxy(new Bean(), (loggerField, obj, type) -> new MethodLogger() {
      @Override
      public void log(@NotNull Level level, String message) {
        throw new NoClassDefFoundError("ch/qos/logback/classic/Logger");
      }

      @Override
      public boolean isLogEnabled(@NotNull Level level) {
        return true;
      }
    });

    assertEquals("Mr. Bean", bean.getName());
    assertEquals("314", assertThrowsExactly(IllegalArgumentException.class, () -> bean.exception(314)).getMessage());
  }


  @Test
  @DisplayName("Failing method logger does not affect method invocation")
  void loggerFailure()
  {
    final var bean = proxy(new Bean(), (loggerField, obj, type) -> new MethodLogger() {
      @Override
      public void log(@NotNull Level level, String message) {
        throw new IllegalStateException("log failed");
      }

      @Override
      public boolean isLogEnabled(@NotNull Level level) {
        return true;
      }
    });

    assertEquals("Mr. Bean", bean.getName());
    assertEquals("314", assertThrowsExactly(IllegalArgumentException.class, () -> bean.exception(314)).getMessage());
  }


  @Test
  @DisplayName("Failing method logger factory does not affect method invocation")
  void loggerFactoryFailure()
  {
    final var bean = proxy(new Bean(), (loggerField, obj, type) -> {
      throw new IllegalStateException("no logger");
    });

    assertEquals("Mr. Bean", bean.getName());
    assertEquals("314", assertThrowsExactly(IllegalArgumentException.class, () -> bean.exception(314)).getMessage());
  }


  /**
   * Creates a class based proxy for {@code target}, logging with the given method logger factory.
   */
  @SuppressWarnings("unchecked")
  private static <T> @NotNull T proxy(@NotNull T target, @NotNull MethodLoggerFactory methodLoggerFactory)
  {
    final var proxyFactory = new ProxyFactory(target);

    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(new MethodLoggingInterceptor(new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public MethodLoggerFactory methodLoggerFactory() {
            return methodLoggerFactory;
          }
        },
        true)));

    return (T)proxyFactory.getProxy();
  }


  private static @NotNull MethodLogger listLogger(@NotNull List<String> log)
  {
    return new MethodLogger() {
      @Override
      public void log(@NotNull Level level, String message) {
        log.add(level.name() + '|' + message);
      }

      @Override
      public boolean isLogEnabled(@NotNull Level level) {
        return true;
      }
    };
  }




  @MethodLoggingConfig(lineNumber = HIDE)
  public static class Bean
  {
    @MethodLogging
    public String getName() {
      return "Mr. Bean";
    }


    @MethodLogging
    public void exception(int id) {
      throw new IllegalArgumentException(Integer.toString(id));
    }


    @MethodLogging
    public Unprintable unprintable(Unprintable value) {
      return value;
    }


    @MethodLogging
    public Cyclic cyclic(Cyclic value) {
      return value;
    }
  }




  public static final class Unprintable
  {
    @Override
    public String toString() {
      throw new IllegalStateException("unprintable");
    }
  }




  /**
   * Object with a cyclic {@code toString()}, as found in entities with bidirectional associations.
   */
  public static final class Cyclic
  {
    private final List<Cyclic> children = List.of(this);


    @Override
    public String toString() {
      return "Cyclic" + children;
    }
  }




  public static class NoLoggerField implements Supplier<String>
  {
    @Override
    @MethodLogging
    public String get() {
      return "result";
    }
  }
}
