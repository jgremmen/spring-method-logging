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
import de.sayayi.lib.methodlogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Exception message")
class ExceptionMessageTest
{
  @Test
  @DisplayName("Exception message is shown by default")
  void shownByDefault()
  {
    assertEquals("INFO|< fail -> IllegalStateException(order 42 shipped)",
        exitMessage(new Bean(), Visibility.SHOW, Bean::fail));
  }


  @Test
  @DisplayName("Exception message is hidden by @MethodLogging")
  void hiddenByMethod()
  {
    assertEquals("INFO|< failHidden -> IllegalStateException",
        exitMessage(new Bean(), Visibility.SHOW, Bean::failHidden));
  }


  @Test
  @DisplayName("Exception message is hidden by @MethodLoggingConfig unless shown by @MethodLogging")
  void hiddenByClass()
  {
    assertEquals("INFO|< fail -> IllegalStateException",
        exitMessage(new HidingBean(), Visibility.SHOW, HidingBean::fail));
    assertEquals("INFO|< failShown -> IllegalStateException(order 42 shipped)",
        exitMessage(new HidingBean(), Visibility.SHOW, HidingBean::failShown));
  }


  @Test
  @DisplayName("Exception message is hidden by the configurer unless shown by @MethodLogging")
  void hiddenByConfigurer()
  {
    assertEquals("INFO|< fail -> IllegalStateException",
        exitMessage(new Bean(), Visibility.HIDE, Bean::fail));
    assertEquals("INFO|< failShown -> IllegalStateException(order 42 shipped)",
        exitMessage(new Bean(), Visibility.HIDE, Bean::failShown));
  }


  /**
   * Invokes {@code invocation} on a proxy for {@code target} and returns the logged exit message.
   */
  @SuppressWarnings("unchecked")
  private static <T> @NotNull String exitMessage(@NotNull T target, @NotNull Visibility defaultExceptionMessage,
                                                 @NotNull Consumer<T> invocation)
  {
    final var log = new ArrayList<String>();
    final var proxyFactory = new ProxyFactory(target);

    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(new MethodLoggingInterceptor(new AnnotationMethodLoggingSource(
        () -> new MethodLoggingConfigurer() {
          @Override
          public MethodLoggerFactory methodLoggerFactory() {
            return (loggerField, obj, type) -> listLogger(log);
          }

          @Override
          public @NotNull Visibility defaultExceptionMessage() {
            return defaultExceptionMessage;
          }
        },
        true)));

    final var proxy = (T)proxyFactory.getProxy();

    assertThrowsExactly(IllegalStateException.class, () -> invocation.accept(proxy));

    return log.getLast();
  }


  private static @NotNull MethodLogger listLogger(@NotNull List<String> log)
  {
    return new MethodLogger() {
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




  @MethodLoggingConfig(lineNumber = HIDE)
  public static class Bean
  {
    @MethodLogging
    public void fail() {
      throw new IllegalStateException("order 42 shipped");
    }


    @MethodLogging(exceptionMessage = HIDE)
    public void failHidden() {
      throw new IllegalStateException("order 42 shipped");
    }


    @MethodLogging(exceptionMessage = SHOW)
    public void failShown() {
      throw new IllegalStateException("order 42 shipped");
    }
  }




  @MethodLoggingConfig(lineNumber = HIDE, exceptionMessage = HIDE)
  public static class HidingBean
  {
    @MethodLogging
    public void fail() {
      throw new IllegalStateException("order 42 shipped");
    }


    @MethodLogging(exceptionMessage = SHOW)
    public void failShown() {
      throw new IllegalStateException("order 42 shipped");
    }
  }
}
