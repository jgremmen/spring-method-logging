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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Method logger factory implementation for Log4j2. Logger fields must hold a Log4j2 logger ({@link Logger}).
 * <p>
 * Use {@link #withFallback()} to log with a logger for the target class if no logger field is available, or
 * {@link #strict()} to require a logger field.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class Log4j2LoggerFactory extends FrameworkMethodLoggerFactory
{
  /**
   * Creates a new Log4j2 logger factory.
   *
   * @param createLoggerOnNoField  if {@code true}, a logger is created even when no logger field is found on the target
   *                               class; if {@code false}, an {@link IllegalStateException} is thrown in that case
   *
   * @deprecated  use {@link #withFallback()} instead of {@code true} or {@link #strict()} instead of {@code false}
   */
  @Deprecated(since = "0.6.0")
  @SuppressWarnings("DeprecatedIsStillUsed")
  public Log4j2LoggerFactory(boolean createLoggerOnNoField)
  {
    super(createLoggerOnNoField, Logger.class,
        clazz -> new Log4j2Logger(LogManager.getLogger(clazz)), Log4j2Logger::of);
  }


  /**
   * Creates a new Log4j2 logger factory which uses a logger for the target class if no logger field is available.
   *
   * @return  new Log4j2 logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull Log4j2LoggerFactory withFallback() {
    return new Log4j2LoggerFactory(true);
  }


  /**
   * Creates a new Log4j2 logger factory which requires a logger field. Method logging for a class without logger field
   * fails when the method is analyzed.
   *
   * @return  new Log4j2 logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull Log4j2LoggerFactory strict() {
    return new Log4j2LoggerFactory(false);
  }
}
