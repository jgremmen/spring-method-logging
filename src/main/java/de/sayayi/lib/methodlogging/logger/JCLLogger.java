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
import org.apache.commons.logging.Log;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

import static java.util.Objects.requireNonNull;


/**
 * {@link MethodLogger} implementation that delegates to an Apache Commons Logging {@link Log}.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 *
 * @see JCLLoggerFactory
 */
@SuppressWarnings("DuplicatedCode")
final class JCLLogger implements MethodLogger
{
  private final @NotNull Log logger;


  /**
   * Creates a new JCL method logger that delegates to the given {@code logger}.
   *
   * @param logger  Commons Logging logger instance, not {@code null}
   */
  JCLLogger(Log logger) {
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
   * Creates a {@code JCLLogger} by reading a Commons Logging {@link Log} from the given field on the target instance.
   * Returns {@link #NO_OP} if the field is not accessible or its value is {@code null}.
   *
   * @param loggerField  the field containing the Commons Logging logger, not {@code null}
   * @param instance     the object owning the field, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   */
  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new JCLLogger((Log)loggerField.get(instance));
    } catch(IllegalAccessException | NullPointerException ex) {
      return NO_OP;
    }
  }
}
