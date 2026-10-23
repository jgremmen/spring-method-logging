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

import static java.util.Objects.requireNonNull;


/**
 * Method logger implementation for the JDK platform logger ({@link System.Logger}).
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
final class SystemLogger implements MethodLogger
{
  private static final System.Logger.Level[] LEVELS = new System.Logger.Level[] {
      null,
      System.Logger.Level.TRACE,
      System.Logger.Level.DEBUG,
      System.Logger.Level.INFO
  };

  private final @NotNull System.Logger logger;


  /**
   * Creates a new method logger that delegates to the given platform {@code logger}.
   *
   * @param logger  platform logger instance, not {@code null}
   */
  SystemLogger(System.Logger logger) {
    this.logger = requireNonNull(logger);
  }


  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isLoggable(LEVELS[level.ordinal()]);
  }


  /**
   * Creates a {@code SystemLogger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a platform logger,
   *          never {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof System.Logger l ? new SystemLogger(l) : NO_OP;
  }
}
