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

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

import static java.util.Objects.requireNonNull;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
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


  Log4j2Logger(Logger logger) {
    this.logger = requireNonNull(logger);
  }


  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isEnabled(LEVELS[level.ordinal()]);
  }


  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new Log4j2Logger((Logger)loggerField.get(instance));
    } catch(IllegalAccessException | NullPointerException ex) {
      return NO_OP;
    }
  }
}
