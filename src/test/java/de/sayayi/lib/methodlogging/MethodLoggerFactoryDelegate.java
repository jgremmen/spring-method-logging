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

import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


/**
 * @author Jeroen Gremmen
 * @version 0.1.0
 */
final class MethodLoggerFactoryDelegate implements MethodLoggerFactory
{
  @Setter
  private MethodLoggerFactory factory;


  @Override
  public @NotNull MethodLogger from(Field loggerField, Object obj) {
    return factory.from(loggerField, obj);
  }
}