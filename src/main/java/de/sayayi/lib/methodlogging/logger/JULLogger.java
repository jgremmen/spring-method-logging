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
package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.*;


/**
 * {@link MethodLogger} implementation that delegates to a Java Util Logging (JUL) {@link Logger}. The levels
 * {@code TRACE}, {@code DEBUG} and {@code INFO} are mapped to the JUL levels {@code FINEST}, {@code FINE} and
 * {@code INFO}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see JULLoggerFactory
 */
final class JULLogger implements MethodLogger
{
  private static final java.util.logging.Level[] LEVELS = new java.util.logging.Level[] { null, FINEST, FINE, INFO };

  private final @NotNull Logger logger;


  /**
   * Creates a new JUL method logger that delegates to the given {@code logger}.
   *
   * @param logger  JUL logger instance, not {@code null}
   */
  JULLogger(Logger logger) {
    this.logger = requireNonNull(logger);
  }


  /**
   * Logs {@code message} with the JUL logger at the corresponding JUL level.
   */
  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  /**
   * {@inheritDoc}
   * <p>
   * JUL records the class and method name of the location; the line number is not supported.
   */
  @Override
  public void log(@NotNull Level level, String message, @NotNull StackTraceElement location) {
    logger.logp(LEVELS[level.ordinal()], location.getClassName(), location.getMethodName(), message);
  }


  /**
   * Tells whether the JUL logger is enabled for the corresponding JUL level.
   */
  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isLoggable(LEVELS[level.ordinal()]);
  }


  /**
   * Creates a {@code JULLogger} by reading a JUL {@link Logger} from the given field on the target instance. Returns
   * {@link #NO_OP} if the field is not accessible, its value is {@code null} or not a JUL logger.
   *
   * @param loggerField  the field containing the JUL logger, not {@code null}
   * @param instance     the object owning the field, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return of(loggerField.get(instance));
    } catch(IllegalAccessException ex) {
      return NO_OP;
    }
  }


  /**
   * Creates a {@code JULLogger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a JUL logger, never
   *          {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof Logger l ? new JULLogger(l) : NO_OP;
  }
}
