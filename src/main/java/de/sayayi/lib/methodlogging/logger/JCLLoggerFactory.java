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
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;


/**
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public final class JCLLoggerFactory extends AbstractMethodLoggerFactory
{
  public JCLLoggerFactory(boolean createLoggerOnNoField) {
    super(createLoggerOnNoField);
  }


  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    return new JCLLogger(LogFactory.getLog(clazz));
  }


  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj) {
    return JCLLogger.from(loggerField, obj);
  }
}