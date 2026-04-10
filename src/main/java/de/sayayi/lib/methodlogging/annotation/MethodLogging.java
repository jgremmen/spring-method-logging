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
import org.intellij.lang.annotations.Language;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Annotation that enables automatic entry/exit logging for a method. When placed on a method, the method logging
 * interceptor will log a message when the method is entered and when it exits, including optional details such as
 * parameter values, return values, elapsed time and line number.
 * <p>
 * Each attribute on this annotation can override the class-level defaults defined by {@link MethodLoggingConfig}.
 * If an attribute is left at its default value, the corresponding setting from {@code @MethodLoggingConfig}
 * (or the global configurer defaults) will be used.
 *
 * <pre>
 *   &#064;MethodLogging(elapsedTime = Visibility.SHOW, entryExitLevel = Level.DEBUG)
 *   public String doWork(&#064;ParamLog(inline = false) String input) {
 *     // ...
 *   }
 * </pre>
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLoggingConfig
 * @see ParamLog
 * @see EnableMethodLogging
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface MethodLogging
{
  /**
   * The field name in this class that contains a loggable instance. The default is the value provided by
   * {@link MethodLoggingConfig#loggerFieldName()}.
   * <p>
   * Generally the loggable instance is a slf4j/jul/log4j logger or a logger from another logging framework but
   * essentially there are no restrictions to the kind of object held by the field. The only constraint is that the
   * method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()} must be able to handle
   * the contents of the field.
   *
   * @return  logger field name
   */
  String loggerFieldName() default "<DEFAULT>";


  /**
   * Indicate whether the method line number is logged or not.
   * <p>
   * The line number is not necessarily the exact line of the method declaration. The reason is that a java class
   * does not provide line information of methods but only of statements. The line number presented is usually the
   * line of the first statement in the method body.
   * <p>
   * If the class does not provide line number information, the visibility is reduced to {@link Visibility#HIDE}.
   *
   * @return  {@link Visibility#SHOW} shows the line number if available, {@link Visibility#HIDE} does not show the
   *          line number, {@link Visibility#DEFAULT} uses the default setting from
   *          {@link MethodLoggingConfig#lineNumber()}
   */
  Visibility lineNumber() default Visibility.DEFAULT;


  /**
   * Indicate whether the elapsed time measured on a method is to be logged on method exit or not.
   *
   * @return  {@link Visibility#SHOW} shows the elapsed time on method exit, {@link Visibility#HIDE} does not show the
   *          elapsed time, {@link Visibility#DEFAULT} uses the default setting from
   *          {@link MethodLoggingConfig#elapsedTime()}
   */
  Visibility elapsedTime() default Visibility.DEFAULT;


  /**
   * Indicate whether method parameters are to be logged or not.
   *
   * @return  {@link Visibility#SHOW} logs the method parameters, {@link Visibility#HIDE} suppresses parameter logging,
   *          {@link Visibility#DEFAULT} uses the default setting from {@link MethodLoggingConfig#parameters()}
   */
  Visibility parameters() default Visibility.DEFAULT;


  /**
   * Indicate whether the method return value is to be logged or not.
   *
   * @return  {@link Visibility#SHOW} logs the return value, {@link Visibility#HIDE} suppresses return value logging,
   *          {@link Visibility#DEFAULT} uses the default setting from {@link MethodLoggingConfig#result()}
   */
  Visibility result() default Visibility.DEFAULT;


  /**
   * The message format used for inline parameters that are displayed as part of the method entry log line
   * (e.g. {@code > method(param=value)}).
   *
   * @return  a message format string, or {@code "<DEFAULT>"} to use the format from
   *          {@link MethodLoggingConfig#inlineParameterFormat()}
   *
   * @see ParamLog#inline()
   */
  @Language("MessageFormat")
  String inlineParameterFormat() default "<DEFAULT>";


  /**
   * The message format used for parameters that are logged on separate lines after the method entry log line.
   *
   * @return  a message format string, or {@code "<DEFAULT>"} to use the format from
   *          {@link MethodLoggingConfig#parameterFormat()}
   *
   * @see ParamLog#inline()
   */
  @Language("MessageFormat")
  String parameterFormat() default "<DEFAULT>";


  /**
   * The message format used for logging the method return value.
   *
   * @return  a message format string, or {@code "<DEFAULT>"} to use the format from
   *          {@link MethodLoggingConfig#resultFormat()}
   */
  @Language("MessageFormat")
  String resultFormat() default "<DEFAULT>";


  /**
   * The log level used for method entry and exit messages.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfig#entryExitLevel()}
   */
  Level entryExitLevel() default Level.DEFAULT;


  /**
   * The log level used for logging method parameters on separate lines.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfig#parameterLevel()}
   */
  Level parameterLevel() default Level.DEFAULT;


  /**
   * The log level used for logging the method return value.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from {@link MethodLoggingConfig#resultLevel()}
   */
  Level resultLevel() default Level.DEFAULT;


  /**
   * Provides an array of parameter names that are to be excluded from method logging. This can be useful for hiding
   * sensitive information such as passwords or tokens.
   *
   * @return  parameter names to exclude, empty by default
   */
  String[] exclude() default {};




  /**
   * Controls the visibility of an optional feature in method logging. Each feature can be explicitly shown or hidden,
   * or left at {@link #DEFAULT} to inherit the setting from the class-level {@link MethodLoggingConfig} or the global
   * configurer defaults.
   */
  enum Visibility
  {
    /** Inherit the visibility from the class-level or global configuration. */
    DEFAULT,

    /** Explicitly enable the feature. */
    SHOW,

    /** Explicitly disable the feature. */
    HIDE;
  }




  /**
   * Defines the log level for method logging messages. Each level can be set explicitly, or left at {@link #DEFAULT}
   * to inherit the setting from the class-level {@link MethodLoggingConfig} or the global configurer defaults.
   */
  enum Level
  {
    /** Inherit the log level from the class-level or global configuration. */
    DEFAULT,

    /** Trace log level */
    TRACE,

    /** Debug log level */
    DEBUG,

    /** Info log level */
    INFO
  }
}
