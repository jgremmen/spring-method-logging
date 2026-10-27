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
  /** Log4j2 levels, indexed by the ordinal of {@link Level}; there is none for {@link Level#DEFAULT}. */
  private static final org.apache.logging.log4j.Level[] LEVELS = new org.apache.logging.log4j.Level[] {
      null,
      org.apache.logging.log4j.Level.TRACE,
      org.apache.logging.log4j.Level.DEBUG,
      org.apache.logging.log4j.Level.INFO
  };

  /** Log4j2 logger receiving the log messages. */
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
   * {@inheritDoc}
   * <p>
   * The message is logged with the Log4j2 logger at the corresponding Log4j2 level.
   */
  @Override
  public void log(@NotNull Level level, @NotNull String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The location is passed to Log4j2 as the source location of the log event.
   *
   * @since 0.6.0
   */
  @Override
  public void log(@NotNull Level level, @NotNull String message, @NotNull StackTraceElement location) {
    logger.atLevel(LEVELS[level.ordinal()]).withLocation(location).log(message);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The Log4j2 logger is queried for the corresponding Log4j2 level.
   */
  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isEnabled(LEVELS[level.ordinal()]);
  }


  /**
   * Creates a {@code Log4j2Logger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a Log4j2 logger, never
   *          {@code null}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof Logger l ? new Log4j2Logger(l) : NO_OP;
  }
}
