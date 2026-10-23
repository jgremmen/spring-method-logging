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

import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.logger.JULLoggerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;


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
        new AnnotationMethodLoggingSource(
            new MethodLoggingConfigurer() {},
            new DefaultResourceLoader()));

    // interceptor applied without pointcut, as in a manually built proxy
    final var proxyFactory = new ProxyFactory((Supplier<String>)() -> "result");
    proxyFactory.addAdvice(interceptor);

    @SuppressWarnings("unchecked")
    final var proxy = (Supplier<String>)proxyFactory.getProxy();

    assertEquals("result", proxy.get());
  }


  @Test
  @DisplayName("Method is invoked if the method analysis fails")
  void failingAnalysis()
  {
    final var source = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {
          @Override
          public MethodLoggerFactory methodLoggerFactory() {
            return JULLoggerFactory.strict();
          }
        },
        new DefaultResourceLoader());

    // proxy mode fails when analyzing the method, as the bean has no logger field...
    assertThrowsExactly(IllegalStateException.class,
        () -> source.getMethodDefinition(NoLoggerField.class.getMethod("get"), NoLoggerField.class));

    // ...while AspectJ mode analyzes on invocation, which must not fail
    final var proxyFactory = new ProxyFactory(new NoLoggerField());
    proxyFactory.addAdvice(new MethodLoggingInterceptor(source));

    @SuppressWarnings("unchecked")
    final var proxy = (Supplier<String>)proxyFactory.getProxy();

    assertEquals("result", proxy.get());
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
