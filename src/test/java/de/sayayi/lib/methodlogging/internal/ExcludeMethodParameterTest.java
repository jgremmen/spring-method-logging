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
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Exclude method parameter")
class ExcludeMethodParameterTest
{
  @Test
  @DisplayName("Parameters are excluded by name")
  void excludeByName() throws Exception
  {
    final var methodDef = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {},
        new DefaultResourceLoader())
        .getMethodDefinition(
            ExcludeParams.class.getMethod("excludeParams", int.class, int.class, String.class, Locale.class),
            ExcludeParams.class);

    // "id" is the @ParamLog name, "p1" the parameter name
    assertEquals(
        List.of("name", "locale"),
        requireNonNull(methodDef)
            .inlineParameters
            .stream()
            .map(ParameterDef::name)
            .toList());
  }


  @Test
  @DisplayName("Generic parameter type is resolved against the target class")
  void genericParameterType() throws Exception
  {
    final var parameterTypes = new ArrayList<Class<?>>();
    final var source = new AnnotationMethodLoggingSource(
        new MethodLoggingConfigurer() {
          @Override
          public boolean excludeMethodParameter(@NotNull ResolvableType methodParameterType)
          {
            parameterTypes.add(methodParameterType.resolve());
            return methodParameterType.toClass() == String.class;
          }
        },
        new DefaultResourceLoader());

    final var methodDef = source.getMethodDefinition(
        AbstractRepository.class.getMethod("save", Object.class, Long.class),
        StringRepository.class);

    assertEquals(List.of(String.class, Long.class), parameterTypes);
    assertEquals(
        List.of("id"),
        requireNonNull(methodDef)
            .inlineParameters
            .stream()
            .map(ParameterDef::name)
            .toList());
  }




  public abstract static class AbstractRepository<T>
  {
    @MethodLogging
    @SuppressWarnings("unused")
    public void save(T entity, Long id) {
    }
  }




  public static class StringRepository extends AbstractRepository<String> {
  }




  public static class ExcludeParams
  {
    @MethodLogging(exclude = { "id", "p1" })
    @SuppressWarnings("unused")
    public void excludeParams(@ParamLog(name = "id") int p0, int p1, String name, Locale locale) {
    }
  }
}
