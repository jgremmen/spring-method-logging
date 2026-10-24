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
package de.sayayi.lib.methodlogging;

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.formatter.DefaultFormatterService;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import de.sayayi.lib.methodlogging.logger.AutoDetectLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;


/**
 * Interface to be implemented by @{@link Configuration} classes annotated with @{@link EnableMethodLogging} that wish
 * or need to specify explicitly how messages are formatted and logged for annotation-driven method logging.
 * <p>
 * All methods have sensible defaults so that only the settings that need customization have to be overridden.
 * <p>
 * At most one configurer bean is used. If the application context contains more than one, one of them must be marked
 * as primary (e.g. with {@code @Primary}); otherwise, the application context fails with a
 * {@code NoUniqueBeanDefinitionException}. The configurer bean is obtained lazily, when the first method with method
 * logging is analyzed or after all singleton beans have been created, whichever comes first, so it is not created early
 * together with the method logging infrastructure. For proxy based method logging, the methods of singleton beans are
 * analyzed after all singleton beans have been created, so the configurer may depend on beans with method logging.
 * Methods invoked by any thread while the configurer is being created are not logged, as waiting for the configurer
 * could deadlock. The configurer methods are invoked once.
 * <p>
 * The configurer provides global defaults for the logger field name, the entry/exit, parameter and result log levels
 * and the line number visibility. All other settings, like the elapsed time, parameter and result visibility, the
 * message formats and the entry/exit prefixes, have no global default; they can only be configured per class with
 * {@link MethodLoggingConfig @MethodLoggingConfig} or per method with {@link MethodLogging @MethodLogging}.
 *
 * @see EnableMethodLogging
 * @see MethodLoggingConfig
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public interface MethodLoggingConfigurer
{
  /**
   * Construct the message support to be used with method/parameter/result logging.
   * <p>
   * If {@code null} is returned (the default), a message support is created with a new {@link DefaultFormatterService}
   * for the class loader of the application context, using a formatter cache size of 128, and the default message
   * factory.
   *
   * @return  Message support or {@code null} to use the default message support
   */
  default MessageSupport messageSupport() {
    return null;
  }


  /**
   * Construct the method logger factory to be used with method logging.
   * <p>
   * The default is an instance of {@link AutoDetectLoggerFactory} which selects the appropriate logger based on the
   * type of the logger held by the logger field. If no logger field is available, it falls back to a JCL (Java Commons
   * Logging) based logger.
   *
   * @return  Method logger factory or {@code null} to use the default method logger factory
   *
   * @see MethodLogger
   */
  default MethodLoggerFactory methodLoggerFactory() {
    return null;
  }


  /**
   * Returns the default logger field name.
   * <p>
   * This value can be overridden on a class level (see {@link MethodLoggingConfig#loggerFieldName()}) or on a method
   * level (see {@link MethodLogging#loggerFieldName()}).
   * <p>
   * An empty name disables the logger field lookup, so the method logger factory is invoked without logger field.
   *
   * @return  logger field name, never {@code null}
   */
  @Contract(pure = true)
  default @NotNull String defaultLoggerFieldName() {
    return "log";
  }


  /**
   * Tells whether a method parameter is to be excluded from logging. This method provides a way to exclude parameters
   * by type, e.g. types which have no meaningful string representation, produce far too much output, contain sensitive
   * information or are not important enough to be logged at all.
   * <p>
   * This method is queried for non-primitive types (except if it is an array, e.g. {@code byte[]}) and method
   * parameters without a @{@link ParamLog} annotation only.
   *
   * @param methodParameterType  method parameter type
   *
   * @return  {@code true} if the parameter must be excluded, {@code false} otherwise
   *
   * @since 0.2.1
   */
  @Contract(pure = true)
  default boolean excludeMethodParameter(@SuppressWarnings("unused") @NotNull ResolvableType methodParameterType) {
    return false;
  }


  /**
   * Returns the default log level for method entry and exit messages. This value is used when neither
   * {@link MethodLoggingConfig#entryExitLevel()} nor {@link MethodLogging#entryExitLevel()} specifies an explicit
   * level.
   * <p>
   * If this level is not enabled for the logger, nothing is logged for the method: parameters and result are not logged
   * either, regardless of their log levels.
   *
   * @return  default entry/exit log level, never {@code null} or {@link Level#DEFAULT}. The default is
   *          {@link Level#INFO}
   *
   * @since 0.2.1
   */
  @Contract(pure = true)
  default @NotNull Level defaultEntryExitLevel() {
    return Level.INFO;
  }


  /**
   * Returns the default log level for parameter logging messages. This value is used when neither
   * {@link MethodLoggingConfig#parameterLevel()} nor {@link MethodLogging#parameterLevel()} specifies an explicit
   * level.
   * <p>
   * Parameters are only logged if method entry and exit messages are logged as well (see
   * {@link #defaultEntryExitLevel()}).
   *
   * @return  default parameter log level, never {@code null} or {@link Level#DEFAULT}. The default is
   *          {@link Level#DEBUG}
   *
   * @since 0.2.1
   */
  @Contract(pure = true)
  default @NotNull Level defaultParameterLevel() {
    return Level.DEBUG;
  }


  /**
   * Returns the default log level for result logging messages. This value is used when neither
   * {@link MethodLoggingConfig#resultLevel()} nor {@link MethodLogging#resultLevel()} specifies an explicit level.
   * <p>
   * Results are only logged if method entry and exit messages are logged as well (see
   * {@link #defaultEntryExitLevel()}).
   *
   * @return  default result log level, never {@code null} or {@link Level#DEFAULT}. The default is {@link Level#DEBUG}
   *
   * @since 0.2.1
   */
  @Contract(pure = true)
  default @NotNull Level defaultResultLevel() {
    return Level.DEBUG;
  }


  /**
   * Returns the default visibility for the method line number. This value is used when neither
   * {@link MethodLoggingConfig#lineNumber()} nor {@link MethodLogging#lineNumber()} specifies an explicit visibility.
   *
   * @return  default line number visibility, never {@code null} or {@link Visibility#DEFAULT}. The default is
   *          {@link Visibility#SHOW}
   *
   * @since 0.2.1
   */
  @Contract(pure = true)
  default @NotNull Visibility defaultLineNumber() {
    return Visibility.SHOW;
  }
}
