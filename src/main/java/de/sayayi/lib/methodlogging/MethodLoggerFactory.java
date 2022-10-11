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

import de.sayayi.lib.methodlogging.logger.GenericMethodLoggerFactory;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


/**
 * A method logger factory is responsible for creating a {@link MethodLogger} for a given object.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see GenericMethodLoggerFactory
 */
public interface MethodLoggerFactory
{
  /**
   * <p>
   *   Creates a new method logger instance based on the given object {@code obj} and optional {@code loggerField}.
   * </p>
   * <p>
   *   This method is invoked every time a method requires logging. In order to reduce performance overhead,
   *   the factory may return cached method loggers.
   * </p>
   *
   * @param loggerField  logger field or {@code null} if no logger field was found
   * @param obj          spring bean to create a method logger for, not {@code null}
   *
   * @return  method logger instance, never {@code null}
   *
   * @see MethodLogger#NO_OP
   */
  @Contract(pure = true)
  @NotNull MethodLogger from(Field loggerField, @NotNull Object obj);
}