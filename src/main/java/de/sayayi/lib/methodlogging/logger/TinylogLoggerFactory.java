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

import de.sayayi.lib.methodlogging.MethodLogger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Method logger factory implementation for Tinylog. Logger fields may hold a Tinylog
 * {@link org.tinylog.TaggedLogger TaggedLogger}; otherwise, the static Tinylog logger is used.
 * <p>
 * Use {@link #withFallback()} to log with the static Tinylog logger if no logger field is available, or
 * {@link #strict()} to require a logger field.
 *
 * @author Jeroen Gremmen
 * @since 0.5.0
 */
public final class TinylogLoggerFactory extends FrameworkMethodLoggerFactory
{
  /**
   * Creates a new Tinylog logger factory.
   *
   * @param createLoggerOnNoField  if {@code true}, a logger is created even when no logger field is found on the target
   *                               class; if {@code false}, an {@link IllegalStateException} is thrown in that case
   *
   * @deprecated  use {@link #withFallback()} instead of {@code true} or {@link #strict()} instead of {@code false}
   */
  @Deprecated(since = "0.6.0")
  @SuppressWarnings("DeprecatedIsStillUsed")
  public TinylogLoggerFactory(boolean createLoggerOnNoField)
  {
    super(createLoggerOnNoField, null,
        clazz -> TinylogLogger.INSTANCE, TinylogLoggerFactory::createFieldMethodLogger);
  }


  /**
   * Creates a new Tinylog logger factory which uses the static Tinylog logger if no logger field is available.
   *
   * @return  new Tinylog logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull TinylogLoggerFactory withFallback() {
    return new TinylogLoggerFactory(true);
  }


  /**
   * Creates a new Tinylog logger factory which requires a logger field. Method logging for a class without logger field
   * fails when the method is analyzed.
   *
   * @return  new Tinylog logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull TinylogLoggerFactory strict() {
    return new TinylogLoggerFactory(false);
  }


  /**
   * Creates a method logger for the tagged logger held by a logger field. If the field does not contain a tagged
   * logger, the static Tinylog logger is used.
   *
   * @param logger  logger instance held by the logger field, may be {@code null}
   *
   * @return  a method logger instance, never {@code null}
   *
   * @since 0.6.0
   */
  private static @NotNull MethodLogger createFieldMethodLogger(Object logger)
  {
    final var methodLogger = TinylogTaggedLogger.of(logger);
    return methodLogger == MethodLogger.NO_OP ? TinylogLogger.INSTANCE : methodLogger;
  }
}
