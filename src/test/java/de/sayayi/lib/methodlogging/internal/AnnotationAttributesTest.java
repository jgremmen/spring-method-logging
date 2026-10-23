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
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.io.DefaultResourceLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Annotation attributes")
class AnnotationAttributesTest
{
  private final AnnotationMethodLoggingSource source = new AnnotationMethodLoggingSource(
      new MethodLoggingConfigurer() {},
      new DefaultResourceLoader());


  @Test
  @DisplayName("Composed annotation meta-annotated with @MethodLogging")
  void composedAnnotation() throws Exception
  {
    final var audited = requireNonNull(source.getMethodDefinition(Bean.class.getMethod("audited"), Bean.class));

    assertEquals(Level.DEBUG, audited.entryExitLevel);
    assertTrue(audited.showElapsedTime);
    assertEquals(Level.TRACE, audited.resultLevel);

    final var auditedInfo =
        requireNonNull(source.getMethodDefinition(Bean.class.getMethod("auditedInfo"), Bean.class));

    assertEquals(Level.INFO, auditedInfo.resultLevel);
  }


  @Test
  @DisplayName("@ParamLog format given by value or format attribute")
  void paramLogFormat() throws Exception
  {
    final var methodDef = requireNonNull(
        source.getMethodDefinition(Bean.class.getMethod("params", String.class, String.class), Bean.class));

    assertEquals("a!", format(methodDef.inlineParameters.get(0), "a"));
    assertEquals("b?", format(methodDef.inlineParameters.get(1), "b"));
  }


  private @NotNull String format(@NotNull ParameterDef parameterDef, @NotNull String value)
  {
    return source
        .getMessageSupport()
        .message(parameterDef.formatMessage().get())
        .with("value", value)
        .format();
  }




  @Target(METHOD)
  @Retention(RUNTIME)
  @MethodLogging(entryExitLevel = Level.DEBUG, elapsedTime = SHOW)
  @interface Audited
  {
    @AliasFor(annotation = MethodLogging.class, attribute = "resultLevel")
    Level resultLevel() default Level.TRACE;
  }




  @SuppressWarnings("unused")
  public static class Bean
  {
    @Audited
    public String audited() {
      return "audited";
    }


    @Audited(resultLevel = Level.INFO)
    public String auditedInfo() {
      return "audited";
    }


    @MethodLogging
    public void params(@ParamLog("%{value}!") String a, @ParamLog(format = "%{value}?") String b) {
    }
  }
}
