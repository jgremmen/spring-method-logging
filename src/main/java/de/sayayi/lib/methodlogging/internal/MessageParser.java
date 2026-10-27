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
package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.message.Message;
import de.sayayi.lib.message.MessageFactory;
import de.sayayi.lib.message.util.SupplierDelegate;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * Parses the message formats of the method logging definitions of a configuration.
 * <p>
 * A message for a built-in default format is created on first use, as many of them are never needed when the
 * corresponding log level is disabled. It is shared by all method logging definitions using the default format, so each
 * default format is parsed at most once. A message for a custom format is created immediately, so an invalid format
 * fails the method analysis.
 * <p>
 * Instances are thread-safe, as methods may be analyzed concurrently.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
final class MessageParser
{
  /** Message factory for parsing the message formats. */
  private final @NotNull MessageFactory messageFactory;

  /** Suppliers of the messages for the built-in default formats, by default format. */
  private final @NotNull Map<String,Supplier<Message>> defaultMessages = new ConcurrentHashMap<>();


  /**
   * Creates a message parser using the given message factory.
   *
   * @param messageFactory  message factory for parsing the message formats, not {@code null}
   */
  MessageParser(@NotNull MessageFactory messageFactory) {
    this.messageFactory = messageFactory;
  }


  /**
   * Returns a supplier of the message for {@code format}. The message for the built-in default format, also used if
   * {@code format} is empty, is created on first use and shared. A message for a custom format is created immediately.
   *
   * @param format         message format or empty for the default format, not {@code null}
   * @param defaultFormat  built-in default format, not {@code null}
   * @param attributeName  name of the annotation attribute providing {@code format}, not {@code null}
   * @param method         method providing {@code format}, not {@code null}
   *
   * @return  message supplier, never {@code null}
   *
   * @throws IllegalStateException  if {@code format} is a custom format which cannot be parsed
   */
  @Contract(pure = true)
  @NotNull Supplier<Message> messageSupplier(@NotNull String format, @NotNull String defaultFormat,
                                             @NotNull String attributeName, @NotNull Method method)
  {
    if (format.isEmpty() || format.equals(defaultFormat))
    {
      return defaultMessages.computeIfAbsent(defaultFormat,
          messageFormat -> SupplierDelegate.of(() -> messageFactory.parseMessage(messageFormat)));
    }

    final Message message;

    try {
      message = messageFactory.parseMessage(format);
    } catch(RuntimeException ex) {
      throw new IllegalStateException("Invalid " + attributeName + " '" + format + "' for " + method, ex);
    }

    return () -> message;
  }
}
