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
package de.sayayi.lib.methodlogging;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


/**
 * Adapter that bridges the method logging framework to an underlying logging implementation (e.g. SLF4J, Log4j2, JUL).
 * Instances are created by a {@link MethodLoggerFactory} and are used by the method logging interceptor to emit entry,
 * exit, parameter and result messages at the configured log level.
 * <p>
 * Exceptions thrown by a method logger do not affect the logged method; they are reported by the method logging
 * interceptor, the first failure of each kind per method at WARN level and subsequent ones at DEBUG level.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see MethodLoggerFactory
 */
public interface MethodLogger
{
  /**
   * A method logger that does nothing.
   *
   * @see MethodLoggerFactory#from(Field, Object, Class)
   */
  @NotNull MethodLogger NO_OP = new MethodLogger() {
    @Override public void log(@NotNull Level level, String message) {}
    @Override public boolean isLogEnabled(@NotNull Level level) { return false; }
  };


  /**
   * Log a message with a specific level to the underlying logging framework.
   *
   * @param level    logging level, never {@code null} or {@link Level#DEFAULT}
   * @param message  message to log, never {@code null}
   */
  void log(@NotNull Level level, String message);


  /**
   * Log a message with a specific level and the source location of the logged method to the underlying logging
   * framework.
   * <p>
   * Logging frameworks supporting an explicit source location use {@code location} for location-aware output, like
   * class, method, file and line number patterns. Otherwise, the location of the method logging implementation is
   * reported. The default implementation ignores the location and invokes {@link #log(Level, String)}.
   *
   * @param level     logging level, never {@code null} or {@link Level#DEFAULT}
   * @param message   message to log, never {@code null}
   * @param location  source location of the logged method, never {@code null}. The file name is {@code null} and the
   *                  line number is negative, if not available
   *
   * @since 0.6.0
   */
  default void log(@NotNull Level level, String message, @NotNull StackTraceElement location) {
    log(level, message);
  }


  /**
   * Checks whether logging for messages with {@code level} is enabled.
   *
   * @param level  logging level to check for, never {@code null} or {@link Level#DEFAULT}
   *
   * @return  {@code true} if logging is enabled, {@code false} otherwise
   */
  @Contract(pure = true)
  boolean isLogEnabled(@NotNull Level level);
}
