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

import de.sayayi.lib.message.util.SupplierDelegate;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.io.DefaultResourceLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Message formats")
class MessageFormatTest
{
  @Test
  @DisplayName("Messages with a default format are created on first use")
  void defaultFormatLazy() throws Exception
  {
    final var methodDef = methodDefinition(Bean.class, "defaults");

    assertInstanceOf(SupplierDelegate.class, methodDef.inlineParameterMessage);
    assertInstanceOf(SupplierDelegate.class, methodDef.parameterMessage);
    assertInstanceOf(SupplierDelegate.class, methodDef.resultMessage);
    assertInstanceOf(SupplierDelegate.class, methodDef.inlineParameters.getFirst().formatMessage());
  }


  @Test
  @DisplayName("Messages with a custom format are created with the method definition")
  void customFormatEager() throws Exception
  {
    final var methodDef = methodDefinition(Bean.class, "custom");

    assertFalse(methodDef.inlineParameterMessage instanceof SupplierDelegate);
    assertFalse(methodDef.parameterMessage instanceof SupplierDelegate);
    assertFalse(methodDef.resultMessage instanceof SupplierDelegate);
    assertFalse(methodDef.inlineParameters.getFirst().formatMessage() instanceof SupplierDelegate);
    assertNotNull(methodDef.resultMessage.get());
  }


  @Test
  @DisplayName("Invalid method format fails the analysis")
  void invalidMethodFormat()
  {
    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> methodDefinition(Bean.class, "invalidResultFormat"));

    assertTrue(ex.getMessage().startsWith("Invalid resultFormat '%{result'"), ex.getMessage());
  }


  @Test
  @DisplayName("Invalid class format fails the analysis")
  void invalidClassFormat()
  {
    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> methodDefinition(InvalidConfigBean.class, "logged"));

    assertTrue(ex.getMessage().startsWith("Invalid parameterFormat '%{value'"), ex.getMessage());
  }


  @Test
  @DisplayName("Invalid parameter format fails the analysis")
  void invalidParameterFormat()
  {
    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> methodDefinition(Bean.class, "invalidParameterFormat"));

    assertTrue(ex.getMessage().startsWith("Invalid @ParamLog format of parameter 'value' '%{value'"), ex.getMessage());
  }


  @Test
  @DisplayName("Invalid parameter format fails the analysis if parameters are hidden")
  void invalidHiddenParameterFormat()
  {
    final var ex = assertThrowsExactly(IllegalStateException.class,
        () -> methodDefinition(Bean.class, "invalidHiddenParameterFormat"));

    assertTrue(ex.getMessage().startsWith("Invalid @ParamLog format of parameter 'value' '%{value'"), ex.getMessage());
  }


  @Test
  @DisplayName("Logging failures are reported first once per phase")
  void firstFailureReport() throws Exception
  {
    final var methodDef = methodDefinition(Bean.class, "defaults");

    assertTrue(methodDef.isFirstFailureReport("entry"));
    assertFalse(methodDef.isFirstFailureReport("entry"));
    assertTrue(methodDef.isFirstFailureReport("exit"));
    assertFalse(methodDef.isFirstFailureReport("exit"));
    assertFalse(methodDef.isFirstFailureReport("entry"));
  }


  private static @NotNull MethodDef methodDefinition(@NotNull Class<?> type, @NotNull String methodName)
      throws NoSuchMethodException
  {
    return requireNonNull(
        new AnnotationMethodLoggingSource(new MethodLoggingConfigurer() {}, new DefaultResourceLoader())
            .getMethodDefinition(type.getMethod(methodName, String.class), type));
  }




  @SuppressWarnings({ "unused", "SameReturnValue" })
  public static class Bean
  {
    @MethodLogging
    public String defaults(String value) {
      return value;
    }


    @MethodLogging(inlineParameterFormat = "%{parameter}: %{value}", parameterFormat = "%{parameter} -> %{value}",
        resultFormat = "returns %{result}")
    public String custom(@ParamLog("[%{value}]") String value) {
      return value;
    }


    @UncheckedResultFormat("%{result")
    public String invalidResultFormat(String value) {
      return value;
    }


    @MethodLogging
    public String invalidParameterFormat(@UncheckedParamLog("%{value") String value) {
      return value;
    }


    @MethodLogging(parameters = HIDE)
    public String invalidHiddenParameterFormat(@UncheckedParamLog("%{value") String value) {
      return value;
    }
  }




  @UncheckedParameterFormat("%{value")
  public static class InvalidConfigBean
  {
    @MethodLogging
    @SuppressWarnings({ "unused", "SameReturnValue" })
    public String logged(String value) {
      return value;
    }
  }




  /*
   * Composed annotations passing formats which are invalid on purpose; their attributes are not checked by the IDE.
   */

  @Retention(RUNTIME)
  @Target(METHOD)
  @MethodLogging
  @interface UncheckedResultFormat
  {
    @AliasFor(annotation = MethodLogging.class, attribute = "resultFormat")
    String value();
  }


  @Retention(RUNTIME)
  @Target(TYPE)
  @MethodLoggingConfig
  @interface UncheckedParameterFormat
  {
    @AliasFor(annotation = MethodLoggingConfig.class, attribute = "parameterFormat")
    String value();
  }


  @Retention(RUNTIME)
  @Target(PARAMETER)
  @ParamLog
  @interface UncheckedParamLog
  {
    @AliasFor(annotation = ParamLog.class, attribute = "format")
    String value();
  }
}
