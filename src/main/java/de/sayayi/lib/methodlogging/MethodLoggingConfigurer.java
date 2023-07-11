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
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import de.sayayi.lib.methodlogging.logger.JCLLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;


/**
 * <p>
 *   Interface to be implemented by @{@link Configuration} classes annotated
 *   with @{@link EnableMethodLogging} that wish or need to specify explicitly how messages are
 *   formatted and logged for annotation-driven method logging.
 * </p>
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public interface MethodLoggingConfigurer
{
  /**
   * <p>
   *   Construct the message support to be used with method/parameter/result logging.
   * </p>
   * <p>
   *   The default message support is constructed for the default locale and uses the shared
   *   message formatter service {@link DefaultFormatterService#getSharedInstance()} and a
   *   message factory without caching capabilities.
   * </p>
   *
   * @return  Message support or {@code null}
   */
  @Contract(pure = true)
  default MessageSupport messageSupport() {
    return null;
  }


  /**
   * <p>
   *   Construct the method logger factory to be used with method logging.
   * </p>
   * <p>
   *   The default is an instance of {@link JCLLoggerFactory} which part of the spring logging framework
   * </p>
   *
   * @return  Method logger factory or {@code null}
   *
   * @see MethodLogger
   */
  @Contract(pure = true)
  default MethodLoggerFactory methodLoggerFactory() {
    return null;
  }


  /**
   * <p>
   *   Returns the default logger field name.
   * </p>
   * <p>
   *   This value can be overridden on a class level (see {@link MethodLoggingConfig#loggerFieldName()}) or on
   *   a method level (see {@link MethodLogging#loggerFieldName()}).
   * </p>
   *
   * @return  logger field name, never {@code null}
   */
  @Contract(pure = true)
  default @NotNull String defaultLoggerFieldName() {
    return "log";
  }


  /**
   * <p>
   *   Tells whether a method parameter is to be excluded from logging. This method provides a way to
   *   exclude parameters which have no meaningful string representation or are not important enough to be
   *   logged at all.
   * </p>
   * <p>
   *   This method is queried for non-primitive types (except if it is an array, eg. {@code byte[]}) and
   *   method parameters without a @{@link ParamLog} annotation only.
   * </p>
   *
   * @param methodParameterType  method parameter type
   *
   * @return  {@code true} if the parameter must be excluded, {@code false} otherwise
   *
   * @since 0.2.1
   */
  @Contract(pure = true)
  default boolean excludeMethodParameter(
      @SuppressWarnings("unused") @NotNull ResolvableType methodParameterType) {
    return false;
  }


  /**
   * @since 0.2.1
   */
  @Contract(pure = true)
  default Level defaultEntryExitLevel() {
    return Level.INFO;
  }


  /**
   * @since 0.2.1
   */
  @Contract(pure = true)
  default Level defaultParameterLevel() {
    return Level.DEBUG;
  }


  /**
   * @since 0.2.1
   */
  @Contract(pure = true)
  default Level defaultResultLevel() {
    return Level.DEBUG;
  }


  /**
   * @since 0.2.1
   */
  @Contract(pure = true)
  default Visibility defaultLineNumber() {
    return Visibility.SHOW;
  }
}
