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

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.aop.support.AopUtils.isAopProxy;
import static org.springframework.aop.support.AopUtils.isJdkDynamicProxy;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Configurer resolution")
class ConfigurerResolutionTest
{
  @Test
  @DisplayName("Configurer is resolved when the first method with method logging is analyzed")
  void lazyConfigurer() throws Exception
  {
    final var resolved = new AtomicInteger();
    final var source = new AnnotationMethodLoggingSource(
        () -> {
          resolved.incrementAndGet();
          return new MethodLoggingConfigurer() {};
        },
        true);

    // creating the interceptor and analyzing methods without method logging do not require the configurer
    new MethodLoggingInterceptor(source);
    assertNull(source.getMethodDefinition(Bean.class.getMethod("notLogged"), Bean.class));
    assertEquals(0, resolved.get());

    assertNotNull(source.getMethodDefinition(Bean.class.getMethod("logged"), Bean.class));
    assertNotNull(source.getMethodDefinition(Bean.class.getMethod("loggedToo"), Bean.class));
    assertEquals(1, resolved.get());
  }


  @ParameterizedTest(name = "{0}")
  @ValueSource(booleans = { false, true })
  @DisplayName("Configuration creation does not deadlock with a thread holding the bean factory lock")
  void noDeadlockWithBeanFactoryLock(boolean lockInConfigurerMethod) throws Exception
  {
    // simulates the singleton lock of the bean factory, acquired when the configurer bean is created or when a
    // configurer method obtains a bean
    final var singletonLock = new ReentrantLock();
    final var resolverWaiting = new CountDownLatch(1);
    final var messageSupportInvocations = new AtomicInteger();
    final Runnable acquireSingletonLock = () -> {
      resolverWaiting.countDown();
      singletonLock.lock();
      singletonLock.unlock();
    };
    final var source = new AnnotationMethodLoggingSource(
        () -> {
          if (!lockInConfigurerMethod)
            acquireSingletonLock.run();

          return new MethodLoggingConfigurer() {
            @Override
            public MessageSupport messageSupport()
            {
              messageSupportInvocations.incrementAndGet();
              if (lockInConfigurerMethod)
                acquireSingletonLock.run();

              return null;
            }
          };
        },
        true);
    final var logged = Bean.class.getMethod("logged");
    final var resolver = new Thread(() -> source.getMethodDefinition(logged, Bean.class));
    final var methodDef = new CompletableFuture<MethodDef>();

    // creates a bean, holding the singleton lock, and invokes a method with method logging, while the resolver thread
    // is creating the configuration and waits for the singleton lock
    final var beanCreator = new Thread(() -> {
      singletonLock.lock();
      try {
        resolver.start();

        if (resolverWaiting.await(5, SECONDS))
          methodDef.complete(source.getMethodDefinition(logged, Bean.class));
        else
          methodDef.completeExceptionally(new AssertionError("resolver thread not started"));
      } catch(Throwable ex) {
        methodDef.completeExceptionally(ex);
      } finally {
        singletonLock.unlock();
      }
    });
    resolver.setDaemon(true);
    beanCreator.setDaemon(true);
    beanCreator.start();

    // the method is not logged while the configuration is being created
    assertNull(methodDef.get(5, SECONDS));

    resolver.join(5000);
    assertFalse(resolver.isAlive());
    assertNotNull(source.getMethodDefinition(logged, Bean.class));
    assertEquals(1, messageSupportInvocations.get());
  }


  @Test
  @DisplayName("Multiple configurers fail unless one is primary")
  void multipleConfigurers()
  {
    final var ex = assertThrows(Exception.class, () -> {
      try(var ignored = new AnnotationConfigApplicationContext(TwoConfigurers.class)) {
        fail("context must not start");
      }
    });
    assertInstanceOf(NoUniqueBeanDefinitionException.class, NestedExceptionUtils.getMostSpecificCause(ex));

    try(var context = new AnnotationConfigApplicationContext(PrimaryConfigurer.class)) {
      assertNotNull(context.getBean(Bean.class));
    }
  }


  @ParameterizedTest(name = "{0}")
  @ValueSource(classes = { ConstructorInjectedConfigurer.class, FieldInjectedConfigurer.class })
  @DisplayName("Configurer may depend on a bean with method logging")
  void configurerWithMethodLoggingDependency(Class<?> configurerClass)
  {
    try(var context = new AnnotationConfigApplicationContext(
        DependentConfigurerConfiguration.class, Dependency.class, configurerClass)) {
      final var dependency = context.getBean(Dependency.class);

      assertTrue(isAopProxy(dependency));

      // invoked while the configurer was created, without method logging
      assertTrue(dependency.getLog().isEmpty());

      dependency.work();
      assertEquals(List.of("INFO|> work", "INFO|< work"), dependency.getLog());
    }
  }


  @Test
  @DisplayName("Failed analysis on first invocation does not affect the method")
  void failedAnalysisOnInvocation() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {}, true);
    final var pointcut = new ProxyMethodLoggingConfiguration.MethodLoggingPointcut(source);
    final var echo = LazyInvalid.class.getMethod("echo", String.class);

    pointcut.analyseDeferredMethods();

    // matched on first invocation of a proxied method; the interceptor reports the failure
    assertThrows(IllegalStateException.class, () -> source.getMethodDefinition(echo, LazyInvalid.class));
    assertTrue(pointcut.matches(echo, LazyInvalid.class));
    assertTrue(pointcut.matches(Invalid.class.getMethod("echo", String.class), LazyInvalid.class));
  }


  @Test
  @DisplayName("All methods of a matched class are analyzed on startup")
  void allMethodsOfMatchedClassAnalyzed() throws Exception
  {
    final var source = new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {}, true);
    final var pointcut = new ProxyMethodLoggingConfiguration.MethodLoggingPointcut(source);

    // Spring stops matching the methods of a class at the first matching method
    assertTrue(pointcut.matches(PartiallyInvalid.class.getMethod("first", String.class), PartiallyInvalid.class));

    final var ex = assertThrowsExactly(IllegalStateException.class, pointcut::analyseDeferredMethods);
    assertTrue(ex.getMessage().contains("doesNotExist"));
  }


  @ParameterizedTest(name = "proxyTargetClass = {0}")
  @ValueSource(classes = { InterfaceProxyConfiguration.class, ClassProxyConfiguration.class })
  @DisplayName("Invalid method of a proxied singleton bean fails the startup")
  void invalidMethodFailsStartup(Class<?> configurationClass)
  {
    final var ex = assertThrows(Exception.class, () -> {
      try(var ignored = new AnnotationConfigApplicationContext(configurationClass)) {
        fail("context must not start");
      }
    });
    assertTrue(NestedExceptionUtils.getMostSpecificCause(ex).getMessage().contains("doesNotExist"));
  }


  @Test
  @DisplayName("Error raised by the analysis on startup fails the startup")
  void errorFailsStartup()
  {
    final var ex = assertThrows(Exception.class, () -> {
      try(var ignored = new AnnotationConfigApplicationContext(ErrorConfiguration.class, ErrorBean.class)) {
        fail("context must not start");
      }
    });
    assertInstanceOf(NoClassDefFoundError.class, NestedExceptionUtils.getRootCause(ex));
  }


  @Test
  @DisplayName("Error raised by the analysis on first invocation does not affect the method")
  void errorOnInvocation()
  {
    try(var context = new AnnotationConfigApplicationContext(ErrorConfiguration.class, LazyErrorBean.class)) {
      final var bean = context.getBean(ErrorApi.class);

      assertTrue(isJdkDynamicProxy(bean));
      assertEquals("a", bean.first("a"));

      for(var n = 0; n < 3; n++)
      {
        final var value = n;
        assertEquals(value, assertDoesNotThrow(() -> bean.poisoned(new Poison(), value)));
      }
    }
  }


  @Test
  @DisplayName("Bean with method logging created for the configurer is analyzed on startup")
  void configurerDependencyAnalyzedOnStartup()
  {
    final var ex = assertThrows(Exception.class, () -> {
      try(var ignored = new AnnotationConfigApplicationContext(
          DependentConfigurerConfiguration.class, LazyInvalid.class, LazyConfigurer.class)) {
        fail("context must not start");
      }
    });
    assertTrue(NestedExceptionUtils.getMostSpecificCause(ex).getMessage().contains("doesNotExist"));
  }


  @ParameterizedTest(name = "{0}")
  @ValueSource(classes = { TwoLazyConfigurers.class, TwoAspectJConfigurers.class })
  @DisplayName("Multiple configurers fail without analyzed methods")
  void multipleConfigurersWithoutAnalyzedMethods(Class<?> configurationClass)
  {
    final var ex = assertThrows(Exception.class, () -> {
      try(var ignored = new AnnotationConfigApplicationContext(configurationClass)) {
        fail("context must not start");
      }
    });
    assertInstanceOf(NoUniqueBeanDefinitionException.class, NestedExceptionUtils.getMostSpecificCause(ex));
  }


  @Test
  @DisplayName("Advisor provides the same pointcut")
  void samePointcut()
  {
    try(var context = new AnnotationConfigApplicationContext(OneConfigurer.class)) {
      final var advisor = context.getBean(PointcutAdvisor.class);

      assertSame(advisor.getPointcut(), advisor.getPointcut());
    }
  }


  @Test
  @DisplayName("Class filter skips classes which cannot carry @MethodLogging")
  void classFilter()
  {
    final var classFilter = new ProxyMethodLoggingConfiguration.MethodLoggingPointcut(
        new AnnotationMethodLoggingSource(() -> new MethodLoggingConfigurer() {}, true)).getClassFilter();

    assertTrue(classFilter.matches(Bean.class));
    assertTrue(classFilter.matches(PartiallyInvalidApi.class));
    assertFalse(classFilter.matches(String.class));
    assertFalse(classFilter.matches(ArrayList.class));
  }




  @SuppressWarnings("unused")
  public static class Bean
  {
    @MethodLogging
    public void logged() {
    }


    @MethodLogging
    public void loggedToo() {
    }


    public void notLogged() {
    }
  }




  @Getter
  @SuppressWarnings("unused")
  public static class Dependency
  {
    private final List<String> log = new ArrayList<>();


    @MethodLogging
    public MethodLoggerFactory methodLoggerFactory()
    {
      return (loggerField, obj, type) -> new MethodLogger() {
        @Override
        public void log(@NotNull Level level, @NotNull String message) {
          log.add(level.name() + '|' + message);
        }

        @Override
        public boolean isLogEnabled(@NotNull Level level) {
          return true;
        }
      };
    }


    @MethodLogging(lineNumber = HIDE)
    public void work() {
    }
  }




  @Component
  public static class ConstructorInjectedConfigurer implements MethodLoggingConfigurer
  {
    private final Dependency dependency;


    public ConstructorInjectedConfigurer(Dependency dependency) {
      this.dependency = dependency;
    }


    @Override
    public MethodLoggerFactory methodLoggerFactory() {
      return dependency.methodLoggerFactory();
    }
  }




  @Component
  public static class FieldInjectedConfigurer implements MethodLoggingConfigurer
  {
    @Autowired private Dependency dependency;


    @Override
    public MethodLoggerFactory methodLoggerFactory() {
      return dependency.methodLoggerFactory();
    }
  }




  public interface Invalid
  {
    String echo(String value);
  }




  @Lazy
  @Component
  public static class LazyInvalid implements Invalid
  {
    @Override
    @MethodLogging(exclude = "doesNotExist")
    public String echo(String value) {
      return value;
    }
  }




  @SuppressWarnings("unused")
  public interface PartiallyInvalidApi
  {
    String first(String value);

    String second(String value);

    String third(String value);
  }




  @SuppressWarnings("unused")
  public static class PartiallyInvalid implements PartiallyInvalidApi
  {
    @Override
    @MethodLogging
    public String first(String value) {
      return value;
    }


    @Override
    @MethodLogging(exclude = "doesNotExist")
    public String second(String value) {
      return value;
    }


    @Override
    @MethodLogging
    public String third(String value) {
      return value;
    }
  }




  @Configuration
  @EnableMethodLogging
  static class InterfaceProxyConfiguration
  {
    @org.springframework.context.annotation.Bean
    public PartiallyInvalidApi partiallyInvalid() {
      return new PartiallyInvalid();
    }
  }




  @Configuration
  @EnableMethodLogging(proxyTargetClass = true)
  static class ClassProxyConfiguration extends InterfaceProxyConfiguration {
  }




  public static final class Poison {
  }




  public interface ErrorApi
  {
    String first(String value);

    int poisoned(Poison poison, int value);
  }




  @Component
  public static class ErrorBean implements ErrorApi
  {
    @Override
    @MethodLogging
    public String first(String value) {
      return value;
    }


    @Override
    @MethodLogging
    public int poisoned(Poison poison, int value) {
      return value;
    }
  }




  @Lazy
  @Component
  public static class LazyErrorBean extends ErrorBean {
  }




  @Configuration
  @EnableMethodLogging
  static class ErrorConfiguration implements MethodLoggingConfigurer
  {
    @Override
    public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
    {
      // e.g. a parameter type referencing a class which is not available
      if (methodParameterType.toClass() == Poison.class)
        throw new NoClassDefFoundError("com/example/Missing");

      return false;
    }
  }




  @Lazy
  @Component
  public static class LazyConfigurer implements MethodLoggingConfigurer
  {
    public LazyConfigurer(ObjectProvider<Invalid> invalid) {
      invalid.getObject();
    }
  }




  @Configuration
  @EnableMethodLogging
  static class DependentConfigurerConfiguration {
  }




  @Configuration
  @EnableMethodLogging
  static class TwoLazyConfigurers
  {
    @Lazy
    @org.springframework.context.annotation.Bean
    public Bean bean() {
      return new Bean();
    }


    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer firstConfigurer() {
      return new MethodLoggingConfigurer() {};
    }


    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer secondConfigurer() {
      return new MethodLoggingConfigurer() {};
    }
  }




  @Configuration
  @EnableMethodLogging(mode = AdviceMode.ASPECTJ)
  static class TwoAspectJConfigurers
  {
    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer firstConfigurer() {
      return new MethodLoggingConfigurer() {};
    }


    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer secondConfigurer() {
      return new MethodLoggingConfigurer() {};
    }
  }




  @Configuration
  @EnableMethodLogging
  static class OneConfigurer
  {
    @org.springframework.context.annotation.Bean
    public Bean bean() {
      return new Bean();
    }


    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer firstConfigurer() {
      return new MethodLoggingConfigurer() {};
    }
  }




  @Configuration
  static class TwoConfigurers extends OneConfigurer
  {
    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer secondConfigurer() {
      return new MethodLoggingConfigurer() {};
    }
  }




  @Configuration
  static class PrimaryConfigurer extends TwoConfigurers
  {
    @Primary
    @org.springframework.context.annotation.Bean
    public MethodLoggingConfigurer primaryConfigurer() {
      return new MethodLoggingConfigurer() {};
    }
  }
}
