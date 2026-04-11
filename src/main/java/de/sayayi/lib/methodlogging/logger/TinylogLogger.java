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
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;


/**
 * @author Jeroen Gremmen
 * @since 0.5.0
 */
@SuppressWarnings("DuplicatedCode")
enum TinylogLogger implements MethodLogger
{
  INSTANCE;


  @Override
  public void log(@NotNull Level level, String message)
  {
    switch(level)
    {
      case TRACE -> Logger.trace(message);
      case DEBUG -> Logger.debug(message);
      case INFO -> Logger.info(message);
    }
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level)
  {
    return switch(level) {
      case TRACE -> Logger.isTraceEnabled();
      case DEBUG -> Logger.isDebugEnabled();
      case INFO -> Logger.isInfoEnabled();
      default -> false;
    };
  }
}
