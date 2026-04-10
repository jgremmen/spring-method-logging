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

import static de.sayayi.lib.methodlogging.MethodLogger.NO_OP;


/**
 * Method logger factory implementation which dynamically selects the appropriate logger based on the type of the
 * logger field.
 * <p>
 * Currently it supports the following logger frameworks:
 * <ul>
 *   <li>Apache logging (org.apache.commons.logging)</li>
 *   <li>JBoss logging (org.jboss.logging)</li>
 *   <li>JDK logging (java.util.logging)</li>
 *   <li>Log4j2 (org.apache.logging.log4j)</li>
 *   <li>Slf4j (org.slf4j)</li>
 *   <li>Logback (ch.qos.logback.classic.Logger)</li>
 *   <li>Tinylog (org.pmw.tinylog.Logger)</li>
 * </ul>
 *
 * @author Jeroen Gremmen
 * @since 0.3.0
 */
public class AutoDetectLoggerFactory extends AbstractMethodLoggerFactory
{
  /** Creates a new auto-detecting method logger factory. */
  public AutoDetectLoggerFactory() {
    super(true);
  }


  /**
   * Creates a JCL-based method logger as a fallback when no logger field is available.
   * {@inheritDoc}
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Class<?> clazz) {
    return new JCLLogger(LogFactory.getLog(clazz));
  }


  /**
   * Creates a method logger matching the type of the logger field. If the field type is not recognized,
   * {@link MethodLogger#NO_OP} is returned.
   * {@inheritDoc}
   */
  @Override
  protected @NotNull MethodLogger createMethodLogger(@NotNull Field loggerField, @NotNull Object obj)
  {
    return switch(loggerField.getType().getName()) {
      case "ch.qos.logback.classic.Logger" -> LogbackLogger.from(loggerField, obj);
      case "java.util.logging.Logger" -> JULLogger.from(loggerField, obj);
      case "org.apache.commons.logging.Log" -> JCLLogger.from(loggerField, obj);
      case "org.apache.logging.log4j.Logger" -> Log4j2Logger.from(loggerField, obj);
      case "org.jboss.logging.Logger" -> JBossLogger.from(loggerField, obj);
      case "org.slf4j.Logger" -> Slf4jLogger.from(loggerField, obj);
      case "org.pmw.tinylog.Logger" -> TinylogLogger.from(obj);
      default -> NO_OP;
    };
  }
}
