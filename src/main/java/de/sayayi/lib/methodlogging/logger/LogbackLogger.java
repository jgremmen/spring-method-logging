/*
 * Copyright 2024 Jeroen Gremmen
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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import de.sayayi.lib.methodlogging.Level;
import de.sayayi.lib.methodlogging.MethodLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

import static ch.qos.logback.core.spi.FilterReply.ACCEPT;
import static ch.qos.logback.core.spi.FilterReply.NEUTRAL;
import static java.util.Objects.requireNonNull;


/**
 * {@link MethodLogger} implementation that delegates to a Logback {@link Logger}.
 *
 * @author Jeroen Gremmen
 * @since 0.4.3
 *
 * @see LogbackLoggerFactory
 */
@SuppressWarnings("DuplicatedCode")
final class LogbackLogger implements MethodLogger
{
  private final @NotNull Logger logger;


  /**
   * Creates a new Logback method logger that delegates to the given {@code logger}.
   *
   * @param logger  Logback logger instance, not {@code null}
   */
  LogbackLogger(Logger logger) {
    this.logger = requireNonNull(logger);
  }


  /**
   * Logs {@code message} with the Logback logger at the corresponding Logback level.
   */
  @Override
  public void log(@NotNull Level level, String message)
  {
    switch(level)
    {
      case TRACE -> logger.trace(message);
      case DEBUG -> logger.debug(message);
      case INFO -> logger.info(message);
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * The location is passed to Logback as caller data of the logging event. As with the regular logging methods of
   * Logback, the turbo filters decide on {@code message} first; the level of the logger only decides if they are
   * neutral.
   *
   * @since 0.6.0
   */
  @Override
  public void log(@NotNull Level level, String message, @NotNull StackTraceElement location)
  {
    final var logbackLevel = switch(level) {
      case TRACE -> ch.qos.logback.classic.Level.TRACE;
      case DEBUG -> ch.qos.logback.classic.Level.DEBUG;
      case INFO -> ch.qos.logback.classic.Level.INFO;
      default -> null;
    };

    if (logbackLevel == null)
      return;

    final var decision = logger
        .getLoggerContext()
        .getTurboFilterList()
        .getTurboFilterChainDecision(null, logger, logbackLevel, message, null, null);

    if (decision == ACCEPT ||
        (decision == NEUTRAL && logbackLevel.isGreaterOrEqual(logger.getEffectiveLevel())))
    {
      final var event = new LoggingEvent(LogbackLogger.class.getName(), logger, logbackLevel, message, null, null);
      event.setCallerData(new StackTraceElement[] { location });

      logger.callAppenders(event);
    }
  }


  /**
   * Tells whether the Logback logger is enabled for the corresponding Logback level.
   */
  @Override
  public boolean isLogEnabled(@NotNull Level level)
  {
    return switch(level) {
      case TRACE -> logger.isTraceEnabled();
      case DEBUG -> logger.isDebugEnabled();
      case INFO -> logger.isInfoEnabled();
      default -> false;
    };
  }


  /**
   * Creates a {@code LogbackLogger} by reading a Logback {@link Logger} from the given field on the target instance.
   * Returns {@link #NO_OP} if the field is not accessible, its value is {@code null} or not a Logback logger.
   *
   * @param loggerField  the field containing the Logback logger, not {@code null}
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
   * Creates a {@code LogbackLogger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a Logback logger,
   *          never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof Logger l ? new LogbackLogger(l) : NO_OP;
  }
}
