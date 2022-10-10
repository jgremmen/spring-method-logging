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
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static lombok.AccessLevel.PRIVATE;


@RequiredArgsConstructor(access = PRIVATE)
@SuppressWarnings("DuplicatedCode")
public final class JULMethodLogger implements MethodLogger
{
  public static final @NotNull MethodLoggerFactory FIELD_FACTORY = JULMethodLogger::from;

  private static final java.util.logging.Level[] LEVELS = new java.util.logging.Level[] {
      null, FINEST, FINE, INFO
  };

  private final Logger logger;


  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isLoggable(LEVELS[level.ordinal()]);
  }


  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new JULMethodLogger((Logger)loggerField.get(instance));
    } catch(IllegalAccessException e) {
      return NO_OP;
    }
  }
}