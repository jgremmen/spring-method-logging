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
import org.intellij.lang.annotations.Language;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.DEFAULT;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Class-level annotation that provides default configuration for all methods annotated with
 * {@link MethodLogging @MethodLogging} within the annotated class. Settings defined here serve as defaults that can
 * be overridden on individual methods via {@code @MethodLogging} attributes.
 * <p>
 * If this annotation is not present on a class, the global defaults from {@link MethodLoggingConfigurer} are used.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLogging
 * @see MethodLoggingConfigurer
 */
@Target(TYPE)
@Retention(RUNTIME)
@SuppressWarnings({"UnknownLanguage", "RedundantSuppression"})
public @interface MethodLoggingConfig
{
  /** Default message format for inline parameters: {@code %{parameter}=%{value}} */
  @Language("MessageFormat")
  String DEFAULT_INLINE_PARAMETER_FORMAT = "%{parameter}=%{value}";

  /** Default message format for separately logged parameters: {@code parameter '%{parameter}' = %{value}} */
  @Language("MessageFormat")
  String DEFAULT_PARAMETER_FORMAT = "parameter '%{parameter}' = %{value}";

  /** Default message format for the method result: {@code result = %{result}} */
  @Language("MessageFormat")
  String DEFAULT_RESULT_FORMAT = "result = %{result}";


  /**
   * The field name in this class that contains a loggable instance. The default is {@code "log"}.
   * <p>
   * Generally the loggable instance is a slf4j/jul/log4j logger, but there are no restrictions to the kind of object
   * held by the field.<br>
   * The method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()} must be able to handle
   * the contents of the field.
   *
   * @return  logger field name
   *
   * @see MethodLoggingConfigurer#defaultLoggerFieldName()
   */
  String loggerFieldName() default "<DEFAULT>";


  /**
   * The prefix prepended to method entry log messages.
   *
   * @return  method entry prefix, defaults to {@code "> "}
   */
  String methodEntryPrefix() default "> ";


  /**
   * The prefix prepended to method exit log messages.
   *
   * @return  method exit prefix, defaults to {@code "< "}
   */
  String methodExitPrefix() default "< ";


  /**
   * Indicate whether the method line number is logged or not.
   * <p>
   * The line number is not necessarily the exact line of the method declaration. The reason is that a java class does
   * not provide line information of methods but only of statements. The line number presented is usually the line of
   * the first statement in the method body.
   * <p>
   * If the class does not provide line number information, the visibility is reduced to {@link Visibility#HIDE}.
   *
   * @return  {@link Visibility#SHOW} (the default) shows the line number if available,
   *          {@link Visibility#HIDE} does not show the line number
   *
   * @see MethodLoggingConfigurer#defaultLineNumber()
   * @see MethodLogging#lineNumber()
   */
  Visibility lineNumber() default Visibility.DEFAULT;


  /**
   * Indicate whether the elapsed time measured on a method is to be logged on method exit or not.
   *
   * @return  {@link Visibility#SHOW} shows the elapsed time on method exit, {@link Visibility#HIDE} (the default)
   *          does not show the elapsed time
   *
   * @see MethodLogging#elapsedTime()
   */
  Visibility elapsedTime() default HIDE;


  /**
   * Indicate whether method parameters are to be logged or not.
   *
   * @return  {@link Visibility#SHOW} (the default) logs the method parameters,
   *          {@link Visibility#HIDE} suppresses parameter logging
   *
   * @see MethodLogging#parameters()
   */
  Visibility parameters() default SHOW;


  /**
   * Indicate whether the method return value is to be logged or not.
   *
   * @return  {@link Visibility#SHOW} (the default) logs the return value,
   *          {@link Visibility#HIDE} suppresses return value logging
   *
   * @see MethodLogging#result()
   */
  Visibility result() default SHOW;


  /**
   * The message format used for inline parameters that are displayed as part of the method entry log line.
   *
   * @return  a message format string, defaults to {@link #DEFAULT_INLINE_PARAMETER_FORMAT}
   *
   * @see MethodLogging#inlineParameterFormat()
   */
  @Language("MessageFormat")
  String inlineParameterFormat() default DEFAULT_INLINE_PARAMETER_FORMAT;


  /**
   * The message format used for parameters that are logged on separate lines after the method entry log line.
   *
   * @return  a message format string, defaults to {@link #DEFAULT_PARAMETER_FORMAT}
   *
   * @see MethodLogging#parameterFormat()
   */
  @Language("MessageFormat")
  String parameterFormat() default DEFAULT_PARAMETER_FORMAT;


  /**
   * The message format used for logging the method return value.
   *
   * @return  a message format string, defaults to {@link #DEFAULT_RESULT_FORMAT}
   *
   * @see MethodLogging#resultFormat()
   */
  @Language("MessageFormat")
  String resultFormat() default DEFAULT_RESULT_FORMAT;


  /**
   * The log level used for method entry and exit messages.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfigurer#defaultEntryExitLevel()}
   *
   * @see MethodLogging#entryExitLevel()
   */
  Level entryExitLevel() default DEFAULT;


  /**
   * The log level used for logging method parameters on separate lines.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfigurer#defaultParameterLevel()}
   *
   * @see MethodLogging#parameterLevel()
   */
  Level parameterLevel() default DEFAULT;


  /**
   * The log level used for logging the method return value.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfigurer#defaultResultLevel()}
   *
   * @see MethodLogging#resultLevel()
   */
  Level resultLevel() default DEFAULT;
}
