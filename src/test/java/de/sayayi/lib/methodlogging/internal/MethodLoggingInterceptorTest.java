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

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Jeroen Gremmen
 * @since 0.5.1
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
}
