/*
 * Copyright 2026 Jeroen Gremmen
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
import org.tinylog.TaggedLogger;

import static java.util.Objects.requireNonNull;


/**
 * {@link MethodLogger} implementation that delegates to a Tinylog {@link TaggedLogger} instance.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 *
 * @see TinylogLoggerFactory
 */
@SuppressWarnings("DuplicatedCode")
final class TinylogTaggedLogger implements MethodLogger
{
  /** Tinylog tagged logger receiving the log messages. */
  private final @NotNull TaggedLogger logger;


  /**
   * Creates a new Tinylog method logger that delegates to the given tagged {@code logger}.
   *
   * @param logger  Tinylog tagged logger instance, not {@code null}
   */
  TinylogTaggedLogger(TaggedLogger logger) {
    this.logger = requireNonNull(logger);
  }


  /**
   * {@inheritDoc}
   * <p>
   * The message is logged with the Tinylog tagged logger at the corresponding Tinylog level.
   */
  @Override
  public void log(@NotNull Level level, @NotNull String message)
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
   * The Tinylog tagged logger is queried for the corresponding Tinylog level.
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
   * Creates a {@code TinylogTaggedLogger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a Tinylog tagged
   *          logger, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof TaggedLogger l ? new TinylogTaggedLogger(l) : NO_OP;
  }
}
