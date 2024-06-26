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
 * Method logger factory implementation which dynamically selects the appropriate logger
 * based on the type of the logger field.
 * <p>
 * Currently it supports the following logger frameworks:
 * <ul>
 *   <li>Apache logging (org.apache.commons.logging)</li>
 *   <li>JDK logging (java.util.logging)</li>
 *   <li>Log4j2 (org.apache.logging.log4j)</li>
 *   <li>Slf4j (org.slf4j)</li>
 *   <li>Logback (ch.qos.logback.classic.Logger)</li>
 * </ul>
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class AutoDetectLoggerFactory extends AbstractMethodLoggerFactory
{
  public AutoDetectLoggerFactory() {
    super(true);
  }


  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    return new JCLLogger(LogFactory.getLog(clazz));
  }


  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField,
                                                     @NotNull Object obj)
  {
    switch(loggerField.getType().getName())
    {
      case "org.apache.commons.logging.Log":
        return JCLLogger.from(loggerField, obj);

      case "java.util.logging.Logger":
        return JULLogger.from(loggerField, obj);

      case "org.apache.logging.log4j.Logger":
        return Log4j2Logger.from(loggerField, obj);

      case "org.slf4j.Logger":
        return Slf4jLogger.from(loggerField, obj);

      case "ch.qos.logback.classic.Logger":
        return LogbackLogger.from(loggerField, obj);
    }

    return MethodLogger.NO_OP;
  }
}
