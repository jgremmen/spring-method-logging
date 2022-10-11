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

import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


/**
 * A method logger is an adapter for the method logging extension which provides access to an underlying
 * logging framework.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public interface MethodLogger
{
  /**
   * A method logger that does nothing.
   *
   * @see MethodLoggerFactory#from(Field, Object)
   */
  @NotNull MethodLogger NO_OP = new MethodLogger() {
    @Override public void log(@NotNull Level level, String message) {}
    @Override public boolean isLogEnabled(@NotNull Level level) { return false; }
  };


  /**
   * Log a message with a specific level to the underlying logging framework.
   *
   * @param level    logging level, not {@code null}
   * @param message  message to log
   */
  void log(@NotNull Level level, String message);


  /**
   * Checks whether logging for messages with {@code level} is enabled.
   *
   * @param level  logging level to check for, not {@code null}
   *
   * @return  {@code true} if logging is enabled, {@code false} otherwise
   */
  @Contract(pure = true)
  boolean isLogEnabled(@NotNull Level level);
}