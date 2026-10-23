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
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.junit.jupiter.api.DisplayName;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.aop.support.AopUtils.isAopProxy;


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
        new DefaultResourceLoader());

    // creating the interceptor and analyzing methods without method logging do not require the configurer
    new MethodLoggingInterceptor(source);
    assertNull(source.getMethodDefinition(Bean.class.getMethod("notLogged"), Bean.class));
    assertEquals(0, resolved.get());

    assertNotNull(source.getMethodDefinition(Bean.class.getMethod("logged"), Bean.class));
    assertNotNull(source.getMethodDefinition(Bean.class.getMethod("loggedToo"), Bean.class));
    assertEquals(1, resolved.get());
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
  @DisplayName("Advisor provides the same pointcut")
  void samePointcut()
  {
    try(var context = new AnnotationConfigApplicationContext(OneConfigurer.class)) {
      final var advisor = context.getBean(PointcutAdvisor.class);

      assertSame(advisor.getPointcut(), advisor.getPointcut());
    }
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
        public void log(@NotNull Level level, String message) {
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




  @Configuration
  @EnableMethodLogging
  static class DependentConfigurerConfiguration {
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
