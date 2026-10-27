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

import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.GenericFormatterService;
import de.sayayi.lib.message.formatter.parameter.runtime.IterableFormatter;
import de.sayayi.lib.message.formatter.parameter.runtime.MapFormatter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Wrap map formatter")
@TestMethodOrder(MethodOrderer.DisplayName.class)
final class WrapMapFormatterTest
{
  private static Stream<Arguments> formattableTypes()
  {
    return Stream.of(
        formattableType(Map.class),
        formattableType(HashMap.class),
        formattableType(TreeMap.class),
        formattableType(ConcurrentHashMap.class)
    );
  }


  @Contract(pure = true)
  private static @NotNull Arguments formattableType(@NotNull Class<?> type) {
    return Arguments.of(Named.of(type.getSimpleName(), type));
  }


  @DisplayName("Formattable types")
  @ParameterizedTest(name = "{0}")
  @MethodSource("formattableTypes")
  void formattableTypes(@NotNull Class<?> type)
  {
    final var formatters = createFormatterService().getFormatters(null, type, null);

    assertTrue(formatters.length >= 2);
    assertInstanceOf(WrapMapFormatter.class, formatters[0]);
    assertInstanceOf(MapFormatter.class, formatters[1]);
  }


  @Test
  @DisplayName("Maps")
  void maps()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("{Dune=2}", format(messageSupport, "%{c}", Map.of("Dune", 2)));
    assertEquals("{a=1, b=2}", format(messageSupport, "%{c}", new TreeMap<>(Map.of("b", 2, "a", 1))));
  }


  @Test
  @DisplayName("Empty or null map")
  void emptyOrNull()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("{}", format(messageSupport, "%{c}", Map.of()));
    assertEquals("", format(messageSupport, "%{c}", null));

    assertEquals("null", format(messageSupport, "%{c,null:null,empty:empty}", null));
    assertEquals("empty", format(messageSupport, "%{c,null:null,empty:empty}", Map.of()));
  }


  @Test
  @DisplayName("Nested maps and iterables")
  void nested()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("{a={b=1}}", format(messageSupport, "%{c}", Map.of("a", Map.of("b", 1))));
    assertEquals("{a=[1, 2]}", format(messageSupport, "%{c}", Map.of("a", List.of(1, 2))));
    assertEquals("[{a=1}]", format(messageSupport, "%{c}", List.of(Map.of("a", 1))));
  }


  @Test
  @DisplayName("Self reference")
  @Disabled("Disabled due to a bug in message format")
  @SuppressWarnings("CollectionAddedToSelf")
  void selfReference()
  {
    final Map<String,Object> map = new LinkedHashMap<>();
    map.put("a", 1);
    map.put("b", map);

    assertEquals("{a=1, b=(this map)}", format(createMessageSupport(), "%{c}", map));
  }


  @Test
  @DisplayName("Spaces around the wrapped value")
  void spaces()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("a {k=v} b", format(messageSupport, "a %{c} b", Map.of("k", "v")));
    assertEquals("a{k=v}b", format(messageSupport, "a%{c}b", Map.of("k", "v")));
    assertEquals("a b", format(messageSupport, "a %{c} b", null));
  }


  @Test
  @DisplayName("Map configuration")
  void configuration()
  {
    final var messageSupport = createMessageSupport();
    final var map = new TreeMap<>(Map.of("a", 1, "b", 2));

    assertEquals("{a: 1; b: 2}", format(messageSupport, "%{c,map-kv:'%{key}: %{value}',list-sep:'; '}", map));
  }


  @Contract(pure = true)
  private static @NotNull GenericFormatterService createFormatterService()
  {
    final var formatterService = new GenericFormatterService();

    formatterService.addFormatter(new IterableFormatter());
    formatterService.addFormatter(new WrapIterableOrArrayFormatter());
    formatterService.addFormatter(new MapFormatter());
    formatterService.addFormatter(new WrapMapFormatter());

    return formatterService;
  }


  @Contract(pure = true)
  private static @NotNull MessageSupport createMessageSupport() {
    return MessageSupportFactory.create(createFormatterService()).setLocale("en-US");
  }


  @Contract(pure = true)
  private static @NotNull String format(@NotNull MessageSupport messageSupport, @NotNull String format,
                                        Object value) {
    return messageSupport.message(format).with("c", value).format();
  }
}
