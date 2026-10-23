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
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;


/**
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
@DisplayName("Configurer validation")
@SuppressWarnings("DataFlowIssue")
class ConfigurerValidationTest
{
  @Test
  @DisplayName("Logger field name must not be null")
  void loggerFieldNameNull()
  {
    assertInvalid("defaultLoggerFieldName() must not return null", new MethodLoggingConfigurer() {
      @Override public @NotNull String defaultLoggerFieldName() {
        return null;
      }
    });
  }


  @Test
  @DisplayName("Entry/exit level must not be null or DEFAULT")
  void entryExitLevel()
  {
    assertInvalid("defaultEntryExitLevel() must not return null", new MethodLoggingConfigurer() {
      @Override public @NotNull Level defaultEntryExitLevel() {
        return null;
      }
    });
    assertInvalid("defaultEntryExitLevel() must not return DEFAULT", new MethodLoggingConfigurer() {
      @Override public @NotNull Level defaultEntryExitLevel() {
        return Level.DEFAULT;
      }
    });
  }


  @Test
  @DisplayName("Parameter level must not be null or DEFAULT")
  void parameterLevel()
  {
    assertInvalid("defaultParameterLevel() must not return null", new MethodLoggingConfigurer() {
      @Override public @NotNull Level defaultParameterLevel() {
        return null;
      }
    });
    assertInvalid("defaultParameterLevel() must not return DEFAULT", new MethodLoggingConfigurer() {
      @Override public @NotNull Level defaultParameterLevel() {
        return Level.DEFAULT;
      }
    });
  }


  @Test
  @DisplayName("Result level must not be null or DEFAULT")
  void resultLevel()
  {
    assertInvalid("defaultResultLevel() must not return null", new MethodLoggingConfigurer() {
      @Override public @NotNull Level defaultResultLevel() {
        return null;
      }
    });
    assertInvalid("defaultResultLevel() must not return DEFAULT", new MethodLoggingConfigurer() {
      @Override public @NotNull Level defaultResultLevel() {
        return Level.DEFAULT;
      }
    });
  }


  @Test
  @DisplayName("Line number visibility must not be null or DEFAULT")
  void lineNumber()
  {
    assertInvalid("defaultLineNumber() must not return null", new MethodLoggingConfigurer() {
      @Override public @NotNull Visibility defaultLineNumber() {
        return null;
      }
    });
    assertInvalid("defaultLineNumber() must not return DEFAULT", new MethodLoggingConfigurer() {
      @Override public @NotNull Visibility defaultLineNumber() {
        return Visibility.DEFAULT;
      }
    });
  }


  private static void assertInvalid(@NotNull String message, @NotNull MethodLoggingConfigurer configurer)
  {
    assertEquals("MethodLoggingConfigurer#" + message, assertThrowsExactly(IllegalStateException.class,
        () -> new AnnotationMethodLoggingSource(configurer, new DefaultResourceLoader())).getMessage());
  }
}
