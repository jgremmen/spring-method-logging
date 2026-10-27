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

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.Visibility;
import org.intellij.lang.annotations.Language;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static de.sayayi.lib.methodlogging.Level.DEFAULT;
import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Class-level annotation that provides default configuration for all methods annotated with
 * {@link MethodLogging @MethodLogging} within the annotated class. Settings defined here serve as defaults that can
 * be overridden on individual methods via {@code @MethodLogging} attributes.
 * <p>
 * If this annotation is not present on a class, the defaults of its attributes are used, as if the class was annotated
 * with {@code @MethodLoggingConfig} without attributes. The logger field name, the log levels and the line number are
 * then taken from {@link MethodLoggingConfigurer}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLogging
 * @see MethodLoggingConfigurer
 */
@Target(TYPE)
@Retention(RUNTIME)
@Documented
@SuppressWarnings({"UnknownLanguage", "RedundantSuppression"})
public @interface MethodLoggingConfig
{
  /** Default message format for inline parameters: {@code %{parameter}=%{value}}. */
  @Language("MessageFormat")
  String DEFAULT_INLINE_PARAMETER_FORMAT = "%{parameter}=%{value}";

  /**
   * Default message format for separately logged parameters: {@code parameter '%{parameter}' = %{value}}.
   */
  @Language("MessageFormat")
  String DEFAULT_PARAMETER_FORMAT = "parameter '%{parameter}' = %{value}";

  /** Default message format for the method result: {@code result = %{result,null:'(null)'}}. */
  @Language("MessageFormat")
  String DEFAULT_RESULT_FORMAT = "result = %{result,null:'(null)'}";


  /**
   * The field name in this class that contains a loggable instance. The default ({@link MethodLogging#DEFAULT_VALUE})
   * uses the field name provided by {@link MethodLoggingConfigurer#defaultLoggerFieldName()}, which is {@code "log"}
   * unless configured otherwise.
   * <p>
   * Generally the loggable instance is a slf4j/jul/log4j logger, but there are no restrictions to the kind of object
   * held by the field.<br>
   * The method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()} must be able to handle
   * the contents of the field.
   * <p>
   * The handling of a field containing {@code null} or an object not supported by the method logger factory depends
   * on the factory. The default factory does not log the method if the field contains {@code null} and falls back to a
   * JCL logger for an unsupported object. The logging framework specific factories do not log the method in either
   * case, except for {@code TinylogLoggerFactory}, which falls back to the static Tinylog logger. A logger field whose
   * declared type can never hold a logger of a logging framework specific factory, e.g. a
   * {@code java.util.logging.Logger} field with {@code Slf4jLoggerFactory}, fails the method analysis with an
   * {@link IllegalStateException} (see
   * {@link de.sayayi.lib.methodlogging.MethodLoggerFactory#validate(java.lang.reflect.Field, Class)
   * MethodLoggerFactory.validate}). An empty name disables the logger field lookup.
   *
   * @return  logger field name
   *
   * @see MethodLoggingConfigurer#defaultLoggerFieldName()
   */
  String loggerFieldName() default MethodLogging.DEFAULT_VALUE;


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
   * Indicates whether the method line number is logged or not.
   * <p>
   * The line number is not necessarily the exact line of the method declaration. The reason is that a java class does
   * not provide line information of methods but only of statements. The line number presented is usually the line of
   * the first statement in the method body.
   * <p>
   * If the class does not provide line number information, the visibility is reduced to {@link Visibility#HIDE}.
   *
   * @return  {@link Visibility#SHOW} shows the line number if available,
   *          {@link Visibility#HIDE} does not show the line number,
   *          {@link Visibility#DEFAULT} (the default) uses the setting from
   *          {@link MethodLoggingConfigurer#defaultLineNumber()}
   *
   * @see MethodLoggingConfigurer#defaultLineNumber()
   * @see MethodLogging#lineNumber()
   */
  Visibility lineNumber() default Visibility.DEFAULT;


  /**
   * Indicates whether the elapsed time measured on a method is to be logged on method exit or not.
   * <p>
   * The elapsed time is measured until the method returns. For methods returning asynchronous or reactive results, like
   * {@link java.util.concurrent.CompletableFuture CompletableFuture} or a Reactor {@code Mono}, it does not include the
   * time until the result is completed.
   *
   * @return  {@link Visibility#SHOW} shows the elapsed time on method exit, {@link Visibility#HIDE} (the default)
   *          does not show the elapsed time, {@link Visibility#DEFAULT} is the same as {@link Visibility#HIDE}
   *
   * @see MethodLogging#elapsedTime()
   */
  Visibility elapsedTime() default HIDE;


  /**
   * Indicates whether the message of an exception thrown by a method is logged on method exit or not. The name of the
   * exception class is always logged.
   *
   * @return  {@link Visibility#SHOW} shows the exception message, {@link Visibility#HIDE} does not show the exception
   *          message, {@link Visibility#DEFAULT} (the default) uses the setting from
   *          {@link MethodLoggingConfigurer#defaultExceptionMessage()}
   *
   * @see MethodLoggingConfigurer#defaultExceptionMessage()
   * @see MethodLogging#exceptionMessage()
   *
   * @since 0.6.0
   */
  Visibility exceptionMessage() default Visibility.DEFAULT;


  /**
   * Indicates whether method parameters are to be logged or not.
   *
   * @return  {@link Visibility#SHOW} (the default) logs the method parameters,
   *          {@link Visibility#HIDE} suppresses parameter logging,
   *          {@link Visibility#DEFAULT} is the same as {@link Visibility#SHOW}
   *
   * @see MethodLogging#parameters()
   */
  Visibility parameters() default SHOW;


  /**
   * Indicates whether the method return value is to be logged or not.
   *
   * @return  {@link Visibility#SHOW} (the default) logs the return value,
   *          {@link Visibility#HIDE} suppresses return value logging,
   *          {@link Visibility#DEFAULT} is the same as {@link Visibility#SHOW}
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
   * <p>
   * If this level is not enabled for the logger, nothing is logged for the method: parameters and result are not logged
   * either, regardless of their log levels.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfigurer#defaultEntryExitLevel()}
   *
   * @see MethodLogging#entryExitLevel()
   */
  Level entryExitLevel() default DEFAULT;


  /**
   * The log level used for logging method parameters on separate lines.
   * <p>
   * The level applies to the inline parameters of the method entry message as well: they are only included if this
   * level is enabled. Parameters are only logged if method entry and exit messages are logged as well (see
   * {@link #entryExitLevel()}).
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfigurer#defaultParameterLevel()}
   *
   * @see MethodLogging#parameterLevel()
   */
  Level parameterLevel() default DEFAULT;


  /**
   * The log level used for logging the method return value.
   * <p>
   * Results are only logged if method entry and exit messages are logged as well (see {@link #entryExitLevel()}).
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfigurer#defaultResultLevel()}
   *
   * @see MethodLogging#resultLevel()
   */
  Level resultLevel() default DEFAULT;
}
