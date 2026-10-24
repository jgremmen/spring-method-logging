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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


/**
 * Annotation that enables automatic entry/exit logging for a method. When placed on a method, the method logging
 * interceptor will log a message when the method is entered and when it exits, including optional details such as
 * parameter values, return values, elapsed time and line number.
 * <p>
 * Each attribute on this annotation can override the class-level defaults defined by {@link MethodLoggingConfig}. If an
 * attribute is left at its default value, the corresponding setting from {@code @MethodLoggingConfig} (or the global
 * configurer defaults) will be used.
 *
 * <pre>
 *   &#064;MethodLogging(elapsedTime = Visibility.SHOW, entryExitLevel = Level.DEBUG)
 *   public String doWork(&#064;ParamLog(inline = false) String input) {
 *     // ...
 *   }
 * </pre>
 *
 * <h2>Resolution of settings</h2>
 * A setting left at its default ({@code DEFAULT} or {@link #DEFAULT_VALUE}) is taken from the next source on the right.
 * If the class has no {@code @MethodLoggingConfig} annotation, the annotation defaults of {@code @MethodLoggingConfig}
 * are used.
 * <table class="striped">
 *   <caption>Sources of method logging settings</caption>
 *   <thead>
 *     <tr>
 *       <th>Setting</th><th>{@code @MethodLogging}</th><th>{@code @MethodLoggingConfig}</th>
 *       <th>{@link MethodLoggingConfigurer}</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr><td>logger field name</td><td>yes</td><td>yes</td><td>{@code defaultLoggerFieldName()}</td></tr>
 *     <tr><td>entry/exit, parameter and result level</td><td>yes</td><td>yes</td>
 *       <td>{@code defaultEntryExitLevel()}, {@code defaultParameterLevel()}, {@code defaultResultLevel()}</td></tr>
 *     <tr><td>line number</td><td>yes</td><td>yes</td><td>{@code defaultLineNumber()}</td></tr>
 *     <tr><td>elapsed time, parameters, result</td><td>yes</td><td>yes</td><td>-</td></tr>
 *     <tr><td>inline parameter, parameter and result format</td><td>yes</td><td>yes</td><td>-</td></tr>
 *     <tr><td>method entry and exit prefix</td><td>-</td><td>yes</td><td>-</td></tr>
 *     <tr><td>excluded parameters</td><td>by name ({@link #exclude()})</td><td>-</td>
 *       <td>by type ({@code excludeMethodParameter(ResolvableType)})</td></tr>
 *   </tbody>
 * </table>
 *
 * <h2>Effect on the logged method</h2>
 * Method logging never affects the logged method: its result and exceptions are passed on unchanged. A parameter value
 * or result which cannot be formatted is logged as {@code <format failed: exception>}. Other logging failures, for
 * example of the method logger, are reported at WARN level by the method logging interceptor through JCL.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLoggingConfig
 * @see ParamLog
 * @see EnableMethodLogging
 */
@Target({ METHOD, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface MethodLogging
{
  /**
   * Value of string attributes indicating that the setting is taken from the next source, e.g. the class-level
   * {@link MethodLoggingConfig} annotation.
   *
   * @since 0.6.0
   */
  @Language("MessageFormat")
  String DEFAULT_VALUE = "<DEFAULT>";


  /**
   * The field name in this class that contains a loggable instance. The default is the value provided by
   * {@link MethodLoggingConfig#loggerFieldName()}.
   * <p>
   * Generally the loggable instance is a slf4j/jul/log4j logger or a logger from another logging framework but
   * essentially there are no restrictions to the kind of object held by the field. The only constraint is that the
   * method logger factory provided by {@link MethodLoggingConfigurer#methodLoggerFactory()} must be able to handle the
   * contents of the field.
   * <p>
   * If the field contains {@code null}, the method is not logged. The handling of an object not supported by the method
   * logger factory depends on the factory: the default factory falls back to a JCL logger, the logging framework
   * specific factories do not log the method. An empty name disables the logger field lookup.
   *
   * @return  logger field name
   */
  String loggerFieldName() default DEFAULT_VALUE;


  /**
   * Indicate whether the method line number is logged or not.
   * <p>
   * The line number is not necessarily the exact line of the method declaration. The reason is that a java class does
   * not provide line information of methods but only of statements. The line number presented is usually the line of
   * the first statement in the method body.
   * <p>
   * If the class does not provide line number information, the visibility is reduced to {@link Visibility#HIDE}.
   *
   * @return  {@link Visibility#SHOW} shows the line number if available, {@link Visibility#HIDE} does not show the line
   *          number, {@link Visibility#DEFAULT} uses the default setting from {@link MethodLoggingConfig#lineNumber()}
   */
  Visibility lineNumber() default Visibility.DEFAULT;


  /**
   * Indicate whether the elapsed time measured on a method is to be logged on method exit or not.
   * <p>
   * The elapsed time is measured until the method returns. For methods returning asynchronous or reactive results, like
   * {@link java.util.concurrent.CompletableFuture CompletableFuture} or a Reactor {@code Mono}, it does not include the
   * time until the result is completed.
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
   * @return  a message format string, or {@link #DEFAULT_VALUE} to use the format from
   *          {@link MethodLoggingConfig#inlineParameterFormat()}
   *
   * @see ParamLog#inline()
   */
  @Language("MessageFormat")
  String inlineParameterFormat() default DEFAULT_VALUE;


  /**
   * The message format used for parameters that are logged on separate lines after the method entry log line.
   *
   * @return  a message format string, or {@link #DEFAULT_VALUE} to use the format from
   *          {@link MethodLoggingConfig#parameterFormat()}
   *
   * @see ParamLog#inline()
   */
  @Language("MessageFormat")
  String parameterFormat() default DEFAULT_VALUE;


  /**
   * The message format used for logging the method return value.
   *
   * @return  a message format string, or {@link #DEFAULT_VALUE} to use the format from
   *          {@link MethodLoggingConfig#resultFormat()}
   */
  @Language("MessageFormat")
  String resultFormat() default DEFAULT_VALUE;


  /**
   * The log level used for method entry and exit messages.
   * <p>
   * If this level is not enabled for the logger, nothing is logged for the method: parameters and result are not logged
   * either, regardless of their log levels.
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfig#entryExitLevel()}
   */
  Level entryExitLevel() default Level.DEFAULT;


  /**
   * The log level used for logging method parameters on separate lines.
   * <p>
   * Parameters are only logged if method entry and exit messages are logged as well (see {@link #entryExitLevel()}).
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from
   *          {@link MethodLoggingConfig#parameterLevel()}
   */
  Level parameterLevel() default Level.DEFAULT;


  /**
   * The log level used for logging the method return value.
   * <p>
   * Results are only logged if method entry and exit messages are logged as well (see {@link #entryExitLevel()}).
   *
   * @return  the log level, or {@link Level#DEFAULT} to use the setting from {@link MethodLoggingConfig#resultLevel()}
   */
  Level resultLevel() default Level.DEFAULT;


  /**
   * Provides an array of parameter names that are to be excluded from method logging. This is useful for parameters
   * whose value has no meaningful string representation, produces far too much output or contains sensitive information
   * such as passwords or tokens.
   * <p>
   * The names are matched against the logged parameter names, i.e. the name given by {@link ParamLog#name()} if
   * present, otherwise the discovered parameter name (or {@code arg0}, {@code arg1}, ... if parameter names are not
   * available). A parameter is excluded even if it is annotated with {@link ParamLog @ParamLog}. To exclude a parameter
   * independent of its name, use {@link ParamLog#exclude()}.
   *
   * @return  parameter names to exclude, empty by default
   */
  String[] exclude() default {};
}
