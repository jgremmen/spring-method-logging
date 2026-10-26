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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;


/**
 * Method logger factory implementation for Logback. Logger fields must hold a Logback logger ({@link Logger}).
 * <p>
 * Use {@link #withFallback()} to log with a logger for the target class if no logger field is available, or
 * {@link #strict()} to require a logger field.
 *
 * @author Jeroen Gremmen
 * @since 0.4.3
 */
public final class LogbackLoggerFactory extends FrameworkMethodLoggerFactory
{
  /**
   * Creates a new Logback logger factory.
   *
   * @param createLoggerOnNoField  if {@code true}, a logger is created even when no logger field is found on the target
   *                               class; if {@code false}, an {@link IllegalStateException} is thrown in that case
   */
  public LogbackLoggerFactory(boolean createLoggerOnNoField) {
    super(createLoggerOnNoField, Logger.class, LogbackLoggerFactory::createClassMethodLogger, LogbackLogger::of);
  }


  /**
   * Creates a new Logback logger factory which uses a logger for the target class if no logger field is available.
   *
   * @return  new Logback logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull LogbackLoggerFactory withFallback() {
    return new LogbackLoggerFactory(true);
  }


  /**
   * Creates a new Logback logger factory which requires a logger field. Method logging for a class without logger field
   * fails when the method is analyzed.
   *
   * @return  new Logback logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull LogbackLoggerFactory strict() {
    return new LogbackLoggerFactory(false);
  }


  /**
   * Creates a Logback method logger for {@code clazz}. If SLF4J is not bound to Logback, or not initialized yet, the
   * SLF4J logger is used instead.
   *
   * @param clazz  the target class, not {@code null}
   *
   * @return  a method logger instance, never {@code null}
   */
  private static @NotNull MethodLogger createClassMethodLogger(@NotNull Class<?> clazz)
  {
    final var logger = LoggerFactory.getLogger(clazz);

    return logger instanceof Logger logbackLogger ? new LogbackLogger(logbackLogger) : new Slf4jLogger(logger);
  }
}
