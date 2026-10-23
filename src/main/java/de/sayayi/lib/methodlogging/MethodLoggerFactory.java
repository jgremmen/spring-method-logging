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

import de.sayayi.lib.methodlogging.logger.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


/**
 * A method logger factory is responsible for creating a {@link MethodLogger} for a given object.
 *
 * @see JBossLoggerFactory
 * @see JCLLoggerFactory
 * @see JULLoggerFactory
 * @see Log4j2LoggerFactory
 * @see Slf4jLoggerFactory
 * @see LogbackLoggerFactory
 * @see TinylogLoggerFactory
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@FunctionalInterface
public interface MethodLoggerFactory
{
  /**
   * Creates a new method logger instance based on the given object {@code obj} and optional {@code loggerField}.
   * <p>
   * This method is invoked every time a method requires logging. In order to reduce performance overhead, the factory
   * may return cached method loggers.
   * <p>
   * If the logger field contains {@code null} or an object the factory does not support, it should return a fallback
   * method logger or {@link MethodLogger#NO_OP}, which disables logging for the method. An exception thrown by this
   * method does not affect the logged method; it is reported at WARN level and the method is invoked without logging.
   *
   * @param loggerField  logger field or {@code null} if no logger field was found
   * @param obj          spring bean to create a method logger for, not {@code null}
   * @param type         target type of {@code obj}, not {@code null}
   *
   * @return  method logger instance, never {@code null}
   *
   * @see AbstractMethodLoggerFactory
   */
  @NotNull MethodLogger from(Field loggerField, @NotNull Object obj, @NotNull Class<?> type);


  /**
   * Validates the logger field found for a method of {@code type}. This method is invoked once, when a method with
   * method logging is analyzed, and allows the factory to fail fast on configuration errors instead of on each
   * invocation. The default implementation accepts any logger field.
   *
   * @param loggerField  logger field or {@code null} if no logger field was found
   * @param type         target type, not {@code null}
   *
   * @throws IllegalStateException  if no method logger can be provided for the logger field
   *
   * @since 0.6.0
   */
  default void validate(Field loggerField, @NotNull Class<?> type) {
  }
}
