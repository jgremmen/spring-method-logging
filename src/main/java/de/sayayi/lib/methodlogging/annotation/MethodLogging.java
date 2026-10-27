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
 *     <tr><td>exception message</td><td>yes</td><td>yes</td><td>{@code defaultExceptionMessage()}</td></tr>
 *     <tr><td>elapsed time, parameters, result</td><td>yes</td><td>yes</td><td>no</td></tr>
 *     <tr><td>inline parameter, parameter and result format</td><td>yes</td><td>yes</td><td>no</td></tr>
 *     <tr><td>method entry and exit prefix</td><td>no</td><td>yes</td><td>no</td></tr>
 *     <tr><td>excluded parameters</td><td>by name ({@link #exclude()})</td><td>no</td>
 *       <td>by type ({@code excludeMethodParameter(ResolvableType)})</td></tr>
 *   </tbody>
 * </table>
 *
 * <h2>Effect on the logged method</h2>
 * The result and exceptions of the logged method are passed on unchanged. A parameter value or result which cannot be
 * formatted is logged as {@code <format failed: exception>}. Other logging failures, for example of the method logger,
 * are reported by the method logging interceptor through JCL: the first failure of each kind per method at WARN level,
 * subsequent ones at DEBUG level.
 * <p>
 * An invalid message format ({@link #inlineParameterFormat()}, {@link #parameterFormat()}, {@link #resultFormat()},
 * the corresponding {@link MethodLoggingConfig} attributes or {@link ParamLog#format()}) fails the method analysis with
 * an {@link IllegalStateException}, with the same effect as an excluded name matching no parameter (see
 * {@link #exclude()}).
 * <p>
 * Formatting a value may however execute code of the value itself. Parameters are formatted before the method is
 * invoked, the result before it is returned to the caller, provided the parameter or result level is enabled. With the
 * default message support (see {@link MethodLoggingConfigurer#messageSupport()}):
 * <ul>
 *   <li>
 *     a {@link java.util.function.Supplier Supplier} (and its primitive variants, like
 *     {@link java.util.function.IntSupplier IntSupplier}) is evaluated by invoking its {@code get} method, so its side
 *     effects occur once more and before the method is invoked;
 *   </li>
 *   <li>
 *     an {@link Iterable} is iterated completely. An iterable which can be iterated only once (e.g. a
 *     {@link java.nio.file.DirectoryStream DirectoryStream}) cannot be iterated by the method or the caller anymore,
 *     a lazily evaluated iterable (e.g. Spring's {@code ObjectProvider}) resolves its elements and an infinite iterable
 *     does not return at all;
 *   </li>
 *   <li>
 *     collections, arrays and maps are formatted with all of their elements. An {@link OutOfMemoryError} raised while
 *     formatting a very large value is passed on, so the method is not invoked (parameters) or its result is lost
 *     (result).
 *   </li>
 * </ul>
 * Values of such types should therefore be excluded from logging: parameters with {@link ParamLog#exclude()},
 * {@link #exclude()} or {@link MethodLoggingConfigurer#excludeMethodParameter(org.springframework.core.ResolvableType)},
 * results with {@link #result()} set to {@link Visibility#HIDE HIDE}.
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
@Documented
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
   */
  String loggerFieldName() default DEFAULT_VALUE;


  /**
   * Indicates whether the method line number is logged or not.
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
   * Indicates whether the elapsed time measured on a method is to be logged on method exit or not.
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
   * Indicates whether the message of an exception thrown by the method is logged on method exit or not. The name of the
   * exception class is always logged.
   * <p>
   * Exception messages often contain input values. Hiding them prevents sensitive data from being logged with the
   * exception.
   *
   * @return  {@link Visibility#SHOW} shows the exception message, {@link Visibility#HIDE} does not show the exception
   *          message, {@link Visibility#DEFAULT} uses the default setting from
   *          {@link MethodLoggingConfig#exceptionMessage()}
   *
   * @since 0.6.0
   */
  Visibility exceptionMessage() default Visibility.DEFAULT;


  /**
   * Indicates whether method parameters are to be logged or not.
   *
   * @return  {@link Visibility#SHOW} logs the method parameters, {@link Visibility#HIDE} suppresses parameter logging,
   *          {@link Visibility#DEFAULT} uses the default setting from {@link MethodLoggingConfig#parameters()}
   */
  Visibility parameters() default Visibility.DEFAULT;


  /**
   * Indicates whether the method return value is to be logged or not.
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
   * The level applies to the inline parameters of the method entry message as well: they are only included if this
   * level is enabled. Parameters are only logged if method entry and exit messages are logged as well (see
   * {@link #entryExitLevel()}).
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
   * A name matches a parameter if it equals the name given by {@link ParamLog#name()} or the discovered parameter name
   * of the method or of any method it overrides, so a parameter named differently in an interface and its
   * implementation can be excluded by either name. If parameter names are not available, the names {@code arg0},
   * {@code arg1}, ... are matched instead of the discovered names. A parameter is excluded even if it is annotated with
   * {@link ParamLog @ParamLog}. To exclude a parameter independent of its name, use {@link ParamLog#exclude()}.
   * <p>
   * A name matching no parameter, e.g. because it is misspelled or the parameter has been renamed, would leave the
   * parameter meant to be excluded in the log output. In that case the method analysis fails with an
   * {@link IllegalStateException}: for proxy based method logging of singleton beans and of beans created while the
   * singleton beans are created (e.g. prototype beans injected into a singleton), the application context fails to
   * start; for beans created later (e.g. lazy beans) and for AspectJ based method logging, the method is invoked
   * without logging. Only if parameter names are available and the names are excluded by a composed annotation
   * (meta-annotated with {@code @MethodLogging}), a name matching no parameter is accepted, so a composed annotation
   * may exclude names not present on every method. The names are validated even for methods without parameters and if
   * parameter logging is disabled with {@link #parameters()} set to {@link Visibility#HIDE}.
   *
   * @return  parameter names to exclude, empty by default
   */
  String[] exclude() default {};
}
