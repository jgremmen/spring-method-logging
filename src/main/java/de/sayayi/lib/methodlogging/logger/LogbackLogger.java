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
import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

import static java.util.Objects.requireNonNull;


/**
 * @author Jeroen Gremmen
 * @since 0.4.3
 */
@SuppressWarnings("DuplicatedCode")
final class LogbackLogger implements MethodLogger
{
  private final @NotNull Logger logger;


  LogbackLogger(Logger logger) {
    this.logger = requireNonNull(logger);
  }


  @Override
  public void log(@NotNull Level level, String message)
  {
    switch(level)
    {
      case TRACE:
        logger.trace(message);
        break;

      case DEBUG:
        logger.debug(message);
        break;

      case INFO:
        logger.info(message);
        break;
    }
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level)
  {
    switch(level)
    {
      case TRACE:
        return logger.isTraceEnabled();

      case DEBUG:
        return logger.isDebugEnabled();

      case INFO:
        return logger.isInfoEnabled();
    }

    return false;
  }


  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new LogbackLogger((Logger)loggerField.get(instance));
    } catch(IllegalAccessException | NullPointerException ex) {
      return NO_OP;
    }
  }
}
