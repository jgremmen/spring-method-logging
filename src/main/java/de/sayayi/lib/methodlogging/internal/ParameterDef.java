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
package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.message.Message;
import de.sayayi.lib.message.MessageSupport;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@SuppressWarnings("UnknownLanguage")
final class ParameterDef implements Serializable
{
  int index;
  String name;
  boolean inline;

  @Language("MessageFormat") String format;
  Message formatMessage;


  @NotNull Message getFormatMessage(@NotNull MessageSupport messageContext)
  {
    synchronized(this) {
      if (formatMessage == null)
      {
        formatMessage = messageContext.message(format).getMessage();
        format = null;
      }

      return formatMessage;
    }
  }
}
