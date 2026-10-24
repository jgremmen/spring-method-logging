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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

import static java.util.Objects.requireNonNull;


/**
 * {@link MethodLogger} implementation that delegates to a Log4j2 {@link Logger}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see Log4j2LoggerFactory
 */
final class Log4j2Logger implements MethodLogger
{
  private static final org.apache.logging.log4j.Level[] LEVELS = new org.apache.logging.log4j.Level[] {
      null,
      org.apache.logging.log4j.Level.TRACE,
      org.apache.logging.log4j.Level.DEBUG,
      org.apache.logging.log4j.Level.INFO
  };

  private final @NotNull Logger logger;


  /**
   * Creates a new Log4j2 method logger that delegates to the given {@code logger}.
   *
   * @param logger  Log4j2 logger instance, not {@code null}
   */
  Log4j2Logger(Logger logger) {
    this.logger = requireNonNull(logger);
  }


  /**
   * Logs {@code message} with the Log4j2 logger at the corresponding Log4j2 level.
   */
  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The location is passed to Log4j2 as the source location of the log event.
   */
  @Override
  public void log(@NotNull Level level, String message, @NotNull StackTraceElement location) {
    logger.atLevel(LEVELS[level.ordinal()]).withLocation(location).log(message);
  }


  /**
   * Tells whether the Log4j2 logger is enabled for the corresponding Log4j2 level.
   */
  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isEnabled(LEVELS[level.ordinal()]);
  }


  /**
   * Creates a {@code Log4j2Logger} by reading a Log4j2 {@link Logger} from the given field on the target instance.
   * Returns {@link #NO_OP} if the field is not accessible, its value is {@code null} or not a Log4j2 logger.
   *
   * @param loggerField  the field containing the Log4j2 logger, not {@code null}
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
   * Creates a {@code Log4j2Logger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a Log4j2 logger, never
   *          {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof Logger l ? new Log4j2Logger(l) : NO_OP;
  }
}
