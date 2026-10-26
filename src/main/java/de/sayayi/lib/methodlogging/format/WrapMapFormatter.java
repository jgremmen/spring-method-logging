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
import de.sayayi.lib.message.part.MessagePart.Text;
import de.sayayi.lib.message.part.TextJoiner;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

import static de.sayayi.lib.message.formatter.FormattableType.DEFAULT_ORDER;


/**
 * Parameter formatter enclosing the formatted entries of maps in braces, e.g. {@code {Dune=2}}, so the entries of a
 * logged parameter can be told apart from the other parameters of the method entry message.
 * <p>
 * The formatter does not format the entries itself. It is registered with an order one below the order of the
 * message-format map formatter ({@code DEFAULT_ORDER - 5}), so it is selected first and delegates to the next
 * formatter, which formats the entries. Iterables and arrays are enclosed in square brackets by
 * {@link WrapIterableOrArrayFormatter}.
 * <p>
 * Instances are stateless and may be shared by multiple formatter services.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
public final class WrapMapFormatter extends AbstractParameterFormatter<Map<?,?>>
{
  /**
   * Formats {@code value} with the next formatter and encloses the result in braces. A result with {@code null} text is
   * returned unchanged; an empty result is enclosed as well, e.g. {@code {}} for an empty map.
   *
   * @param context  formatter context, not {@code null}
   * @param value    map to format, not {@code null}
   *
   * @return  formatted value, never {@code null}
   */
  @Override
  protected @NotNull Text formatValue(@NotNull ParameterFormatterContext context, @NotNull Map<?,?> value)
  {
    final var result = context.delegateToNextFormatter();
    return result.getText() == null ? result : new TextJoiner().add('{').add(result).add('}').asNoSpaceText();
  }


  /**
   * Returns the wrapped type {@link Map}, registered with an order one below the order of the map formatter, so this
   * formatter precedes it.
   *
   * @return  formattable types, never {@code null}
   */
  @Override
  public @NotNull Set<FormattableType> getFormattableTypes() {
    return Set.of(new FormattableType(Map.class, DEFAULT_ORDER - 6));
  }
}
