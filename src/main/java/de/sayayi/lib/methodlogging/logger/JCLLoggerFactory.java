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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Method logger factory implementation for Apache Commons Logging (JCL). Logger fields must hold a JCL logger
 * ({@link Log}).
 * <p>
 * Use {@link #withFallback()} to log with a logger for the target class if no logger field is available, or
 * {@link #strict()} to require a logger field.
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class JCLLoggerFactory extends FrameworkMethodLoggerFactory
{
  /**
   * Creates a new JCL logger factory.
   *
   * @param createLoggerOnNoField  if {@code true}, a logger is created even when no logger field is found on the target
   *                               class; if {@code false}, an {@link IllegalStateException} is thrown in that case
   */
  public JCLLoggerFactory(boolean createLoggerOnNoField) {
    super(createLoggerOnNoField, Log.class, clazz -> new JCLLogger(LogFactory.getLog(clazz)), JCLLogger::of);
  }


  /**
   * Creates a new JCL logger factory which uses a logger for the target class if no logger field is available.
   *
   * @return  new JCL logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull JCLLoggerFactory withFallback() {
    return new JCLLoggerFactory(true);
  }


  /**
   * Creates a new JCL logger factory which requires a logger field. Method logging for a class without logger field
   * fails when the method is analyzed.
   *
   * @return  new JCL logger factory, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(value = "-> new", pure = true)
  public static @NotNull JCLLoggerFactory strict() {
    return new JCLLoggerFactory(false);
  }
}
