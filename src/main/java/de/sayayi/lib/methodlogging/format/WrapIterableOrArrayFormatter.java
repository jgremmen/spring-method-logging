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
package de.sayayi.lib.methodlogging.format;

import de.sayayi.lib.message.formatter.FormattableType;
import de.sayayi.lib.message.formatter.parameter.AbstractParameterFormatter;
import de.sayayi.lib.message.formatter.parameter.ParameterFormatterContext;
import de.sayayi.lib.message.formatter.parameter.runtime.ArrayFormatter;
import de.sayayi.lib.message.formatter.parameter.runtime.IterableFormatter;
import de.sayayi.lib.message.part.MessagePart.Text;
import de.sayayi.lib.message.part.TextJoiner;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableSet;


/**
 * Parameter formatter enclosing the formatted elements of iterables and arrays in square brackets, e.g.
 * {@code [1, 2, 3]}, so the elements of a logged parameter can be told apart from the other parameters of the method
 * entry message.
 * <p>
 * The formatter does not format the elements itself. It is registered with an order one below the orders of the
 * message-format {@link ArrayFormatter} and {@link IterableFormatter}, so it is selected first and delegates to the
 * next formatter, which formats the elements. The result of that formatter is enclosed as is: a {@code char[]}
 * formatted as text by the string formatter of a
 * {@link de.sayayi.lib.message.formatter.DefaultFormatterService DefaultFormatterService}, for example, is logged as
 * {@code [ab]}. Maps are enclosed in braces by {@link WrapMapFormatter}.
 * <p>
 * Instances are stateless and may be shared by multiple formatter services.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
public final class WrapIterableOrArrayFormatter extends AbstractParameterFormatter<Object>
{
  /**
   * Formats {@code value} with the next formatter and encloses the result in square brackets. A result with
   * {@code null} text is returned unchanged; an empty result is enclosed as well, e.g. {@code []} for an empty list.
   *
   * @param context  formatter context, not {@code null}
   * @param value    iterable or array to format, not {@code null}
   *
   * @return  formatted value, never {@code null}
   */
  @Override
  protected @NotNull Text formatValue(@NotNull ParameterFormatterContext context, @NotNull Object value)
  {
    final var result = context.delegateToNextFormatter();
    return result.getText() == null ? result : new TextJoiner().add('[').add(result).add(']').asNoSpaceText();
  }


  /**
   * Returns the wrapped types: arrays of all primitive types, object arrays, the atomic array types and
   * {@link Iterable}. Each type is registered with an order one below the order the {@link ArrayFormatter} or
   * {@link IterableFormatter} registers for it, or one below its default order if neither registers it, so this
   * formatter precedes the list formatters.
   *
   * @return  formattable types, never {@code null}
   */
  @Override
  public @NotNull Set<FormattableType> getFormattableTypes()
  {
    final var minFormatterOrderMap = Stream
        .of(new ArrayFormatter(), new IterableFormatter())
        .flatMap(formatter -> formatter.getFormattableTypes().stream())
        .collect(toMap(FormattableType::getType, FormattableType::getOrder, Math::min));

    return Stream
        .of(boolean[].class,
            byte[].class,
            char[].class,
            short[].class,
            int[].class,
            long[].class,
            float[].class,
            double[].class,
            Object[].class,
            AtomicIntegerArray.class,
            AtomicLongArray.class,
            AtomicReferenceArray.class,
            Iterable.class)
        .map(type -> new FormattableType(type,
            minFormatterOrderMap.getOrDefault(type, new FormattableType(type).getOrder()) - 1))
        .collect(toUnmodifiableSet());
  }
}
