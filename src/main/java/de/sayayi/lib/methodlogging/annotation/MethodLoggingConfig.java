/*
 * Copyright 2022 Jeroen Gremmen
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
package de.sayayi.lib.methodlogging.annotation;

import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.INFO;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface MethodLoggingConfig
{
  String DEFAULT_PARAMETER_FORMAT = "parameter '%{parameter}' = %{value}";
  String DEFAULT_INLINE_PARAMETER_FORMAT = "%{parameter}=%{value}";
  String DEFAULT_RESULT_FORMAT = "result = %{result}";


  /**
   * <p>
   *   The field name in this class that contains a loggable instance. The default is {@code "logger"}.
   * </p>
   * <p>
   *   Generally the loggable instance is a slf4j/jul/log4j logger but there are no restrictions to the kind
   *   of object held by the field.<br>
   *   The method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()}
   *   must be able to handle the contents of the field.
   * </p>
   *
   * @return  logger field name
   */
  String loggerFieldName() default "logger";

  String methodEntryPrefix() default "> ";

  String methodExitPrefix() default "< ";

  Visibility lineNumber() default SHOW;

  Visibility elapsedTime() default HIDE;

  Visibility parameters() default SHOW;

  Visibility result() default SHOW;

  String inlineParameterFormat() default DEFAULT_INLINE_PARAMETER_FORMAT;

  String parameterFormat() default DEFAULT_PARAMETER_FORMAT;

  String resultFormat() default DEFAULT_RESULT_FORMAT;

  Level entryExitLevel() default INFO;

  Level parameterLevel() default DEBUG;

  Level resultLevel() default DEBUG;
}