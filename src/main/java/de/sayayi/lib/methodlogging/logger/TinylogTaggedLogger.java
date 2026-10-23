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

import java.lang.reflect.Field;

import static java.util.Objects.requireNonNull;


/**
 * Method logger implementation for a tinylog {@link TaggedLogger} instance.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@SuppressWarnings("DuplicatedCode")
final class TinylogTaggedLogger implements MethodLogger
{
  private final @NotNull TaggedLogger logger;


  /**
   * Creates a new tinylog method logger that delegates to the given tagged {@code logger}.
   *
   * @param logger  tinylog tagged logger instance, not {@code null}
   */
  TinylogTaggedLogger(TaggedLogger logger) {
    this.logger = requireNonNull(logger);
  }


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
   * Creates a {@code TinylogTaggedLogger} by reading a tinylog {@link TaggedLogger} from the given field on the target
   * instance. Returns {@link #NO_OP} if the field is not accessible, its value is {@code null} or not a tagged logger.
   *
   * @param loggerField  the field containing the tagged logger, not {@code null}
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
   * Creates a {@code TinylogTaggedLogger} for the given logger instance.
   *
   * @param logger  logger instance, may be {@code null}
   *
   * @return  a method logger instance or {@link #NO_OP} if {@code logger} is {@code null} or not a tinylog tagged
   *          logger, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger of(Object logger) {
    return logger instanceof TaggedLogger l ? new TinylogTaggedLogger(l) : NO_OP;
  }
}
