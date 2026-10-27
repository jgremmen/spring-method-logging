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

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.FormattableType;
import de.sayayi.lib.message.formatter.GenericFormatterService;
import de.sayayi.lib.message.formatter.parameter.named.BoolFormatter;
import de.sayayi.lib.message.formatter.parameter.named.ChoiceFormatter;
import de.sayayi.lib.message.formatter.parameter.named.SizeFormatter;
import de.sayayi.lib.message.formatter.parameter.runtime.*;
import de.sayayi.lib.message.formatter.post.runtime.CasePostFormatter;
import de.sayayi.lib.message.formatter.post.runtime.ClipPostFormatter;
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import de.sayayi.lib.methodlogging.format.WrapIterableOrArrayFormatter;
import de.sayayi.lib.methodlogging.format.WrapMapFormatter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static de.sayayi.lib.message.formatter.FormattableType.DEFAULT_ORDER;


/**
 * Creates the default message support of method logging, used if the configurer provides none (see
 * {@link MethodLoggingConfigurer#messageSupport()}).
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
final class DefaultMessageSupport
{
  /** Prevents instantiation, as this class provides static methods only. */
  private DefaultMessageSupport() {}


  /**
   * Creates the default message support, used if the configurer provides none.
   * <p>
   * The set of formatters is deliberately minimal. It covers what custom formats need to adjust the output, in
   * {@link ParamLog#format()} or the format attributes of {@link MethodLogging} and {@link MethodLoggingConfig}: the
   * runtime formatters for optionals, suppliers, references, enums and collections, the named formatters {@code bool},
   * {@code choice} and {@code size}, and the post formatters {@code case} and {@code clip}. All other values, including
   * numbers, dates, paths and exceptions, are formatted by their {@code toString()} representation, independent of the
   * locale. An application requiring more formatters provides its own message support with
   * {@link MethodLoggingConfigurer#messageSupport()}.
   * <p>
   * Iterables and arrays are enclosed in square brackets, maps in braces. Without them, the elements of a collection
   * parameter could not be told apart from the other parameters in the parameter list of the method entry message,
   * as both are separated by commas. Collections and maps are limited to 20 elements.
   *
   * @return  sealed default message support, never {@code null}
   *
   * @since 0.6.0
   */
  @Contract(pure = true)
  @SuppressWarnings("UnnecessaryUnicodeEscape")
  static @NotNull MessageSupport create()
  {
    final var formatterService = new GenericFormatterService(256);
    final var stringFormatter = formatterService
        .getFormatters("string", String.class, null)[0];

    // Add default formatters
    formatterService.addFormatter(new AtomicBooleanFormatter());
    formatterService.addFormatter(new BooleanSupplierFormatter());
    formatterService.addFormatter(new DoubleSupplierFormatter());
    formatterService.addFormatter(new EnumFormatter());
    formatterService.addFormatter(new IntSupplierFormatter());
    formatterService.addFormatter(new IterableFormatter());
    formatterService.addFormatter(new LongSupplierFormatter());
    formatterService.addFormatter(new OptionalFormatter());
    formatterService.addFormatter(new OptionalDoubleFormatter());
    formatterService.addFormatter(new OptionalIntFormatter());
    formatterService.addFormatter(new OptionalLongFormatter());
    formatterService.addFormatter(new ReferenceFormatter());
    formatterService.addFormatter(new SupplierFormatter());
    formatterService.addFormatter(new ToTemporalDelegate());

    // Fix conflicting types
    formatterService.addFormatterForType(
        new FormattableType(Path.class, DEFAULT_ORDER - 2), stringFormatter);
    formatterService.addFormatterForType(
        new FormattableType(Throwable.class, DEFAULT_ORDER - 2), stringFormatter);

    // Configure list formatting
    final var arrayFormatter = new ArrayFormatter();
    formatterService.addFormatter(arrayFormatter);
    formatterService.addFormatterForType(new FormattableType(char[].class), arrayFormatter);
    formatterService.addFormatter(new WrapIterableOrArrayFormatter());

    // Configure map formatting
    formatterService.addFormatter(new MapFormatter());
    formatterService.addFormatter(new WrapMapFormatter());

    // Add named formatters, only used by formats selecting them
    formatterService.addFormatter(new BoolFormatter());
    formatterService.addFormatter(new ChoiceFormatter());
    formatterService.addFormatter(new SizeFormatter());

    // Add post formatters
    formatterService.addPostFormatter(new CasePostFormatter());
    formatterService.addPostFormatter(new ClipPostFormatter());

    final var messageSupport = MessageSupportFactory.create(formatterService.seal());

    // Default list settings
    messageSupport.setDefaultConfig("list-max-size", 20);
    messageSupport.setDefaultConfig("list-value-more", "\u2026");

    return messageSupport.seal();
  }
}
