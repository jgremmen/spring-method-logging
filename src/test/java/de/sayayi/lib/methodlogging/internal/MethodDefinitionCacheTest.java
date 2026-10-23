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
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodClassKey;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Optional;

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
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader());
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
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader());
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
        new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType) {
            // e.g. a configurer delegating to a bean with method logging
            return source[0].getMethodDefinition(otherMethod, Bean.class) != null;
          }
        },
        new DefaultResourceLoader());

    assertNotNull(source[0].getMethodDefinition(Bean.class.getMethod("logged", String.class), Bean.class));
    assertEquals(2, source[0].methodLoggingDefinitionCache.size());
  }




  public static class Bean
  {
    @MethodLogging
    public void logged(@SuppressWarnings("unused") String value) {
    }


    @SuppressWarnings("EmptyMethod")
    public void notLogged() {
    }
  }
}
