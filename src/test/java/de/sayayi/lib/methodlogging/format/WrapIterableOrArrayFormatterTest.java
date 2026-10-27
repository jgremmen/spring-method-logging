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
import de.sayayi.lib.message.formatter.FormattableType;
import de.sayayi.lib.message.formatter.GenericFormatterService;
import de.sayayi.lib.message.formatter.parameter.ParameterFormatter;
import de.sayayi.lib.message.formatter.parameter.runtime.ArrayFormatter;
import de.sayayi.lib.message.formatter.parameter.runtime.IterableFormatter;
import de.sayayi.lib.message.formatter.parameter.runtime.NumberFormatter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Wrap iterable or array formatter")
@TestMethodOrder(MethodOrderer.DisplayName.class)
final class WrapIterableOrArrayFormatterTest
{
  private static Stream<Arguments> formattableTypes()
  {
    return Stream.of(
        formattableType(boolean[].class, ArrayFormatter.class),
        formattableType(byte[].class, ArrayFormatter.class),
        formattableType(char[].class, ArrayFormatter.class),
        formattableType(short[].class, ArrayFormatter.class),
        formattableType(int[].class, ArrayFormatter.class),
        formattableType(long[].class, ArrayFormatter.class),
        formattableType(float[].class, ArrayFormatter.class),
        formattableType(double[].class, ArrayFormatter.class),
        formattableType(Object[].class, ArrayFormatter.class),
        formattableType(String[].class, ArrayFormatter.class),
        formattableType(AtomicIntegerArray.class, ArrayFormatter.class),
        formattableType(AtomicLongArray.class, ArrayFormatter.class),
        formattableType(AtomicReferenceArray.class, ArrayFormatter.class),
        formattableType(Iterable.class, IterableFormatter.class),
        formattableType(ArrayList.class, IterableFormatter.class),
        formattableType(Set.class, IterableFormatter.class)
    );
  }


  @Contract(pure = true)
  private static @NotNull Arguments formattableType(@NotNull Class<?> type,
                                                    @NotNull Class<? extends ParameterFormatter> nextFormatterType) {
    return Arguments.of(Named.of(type.getSimpleName(), type), nextFormatterType);
  }


  @DisplayName("Formattable types")
  @ParameterizedTest(name = "{0}")
  @MethodSource("formattableTypes")
  void formattableTypes(@NotNull Class<?> type, @NotNull Class<? extends ParameterFormatter> nextFormatterType)
  {
    final var formatters = createFormatterService().getFormatters(null, type, null);

    assertTrue(formatters.length >= 2);
    assertInstanceOf(WrapIterableOrArrayFormatter.class, formatters[0]);
    assertInstanceOf(nextFormatterType, formatters[1]);
  }


  @Test
  @DisplayName("Primitive arrays")
  void primitiveArrays()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("[true, false]", format(messageSupport, "%{c}", new boolean[] { true, false }));
    assertEquals("[1, -2]", format(messageSupport, "%{c}", new byte[] { 1, -2 }));
    assertEquals("[a, b]", format(messageSupport, "%{c}", new char[] { 'a', 'b' }));
    assertEquals("[3, 4]", format(messageSupport, "%{c}", new short[] { 3, 4 }));
    assertEquals("[12, -7, 99]", format(messageSupport, "%{c}", new int[] { 12, -7, 99 }));
    assertEquals("[5, 6]", format(messageSupport, "%{c}", new long[] { 5, 6 }));
    assertEquals("[1.5, 2.25]", format(messageSupport, "%{c}", new float[] { 1.5f, 2.25f }));
    assertEquals("[0.5]", format(messageSupport, "%{c}", new double[] { 0.5 }));
  }


  @Test
  @DisplayName("Object array")
  void objectArray()
  {
    assertEquals("[Test, true, -6]",
        format(createMessageSupport(), "%{c}", new Object[] { "Test", true, null, -6 }));
  }


  @Test
  @DisplayName("Atomic arrays")
  void atomicArrays()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("[1, 2]", format(messageSupport, "%{c}", new AtomicIntegerArray(new int[] { 1, 2 })));
    assertEquals("[3, 4]", format(messageSupport, "%{c}", new AtomicLongArray(new long[] { 3, 4 })));
    assertEquals("[A, B]", format(messageSupport, "%{c}", new AtomicReferenceArray<>(new String[] { "A", "B" })));
  }


  @Test
  @DisplayName("Iterables")
  void iterables()
  {
    final var messageSupport = createMessageSupport();
    final Iterable<String> iterable = () -> List.of("X", "Y").iterator();

    assertEquals("[Test, true, -6]", format(messageSupport, "%{c}", Arrays.asList("Test", true, null, -6)));
    assertEquals("[A]", format(messageSupport, "%{c}", Set.of("A")));
    assertEquals("[X, Y]", format(messageSupport, "%{c}", iterable));
  }


  @Test
  @DisplayName("Nested arrays and iterables")
  void nested()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("[[1, 2], [3]]", format(messageSupport, "%{c}", List.of(List.of(1, 2), List.of(3))));
    assertEquals("[[1, 2], [A]]", format(messageSupport, "%{c}", new Object[] { new int[] { 1, 2 }, Set.of("A") }));
  }


  @Test
  @DisplayName("Self reference")
  @SuppressWarnings("CollectionAddedToSelf")
  void selfReference()
  {
    final List<Object> list = new ArrayList<>();
    list.add("A");
    list.add(list);

    final var array = new Object[2];
    array[0] = "B";
    array[1] = array;

    final var messageSupport = createMessageSupport();

    assertEquals("[A, (this collection)]", format(messageSupport, "%{c}", list));
    assertEquals("[B, (this array)]", format(messageSupport, "%{c}", array));
  }


  @Test
  @DisplayName("Empty or null array and iterable")
  void emptyOrNull()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("[]", format(messageSupport, "%{c}", new int[0]));
    assertEquals("[]", format(messageSupport, "%{c}", List.of()));
    assertEquals("", format(messageSupport, "%{c}", null));

    assertEquals("null", format(messageSupport, "%{c,null:null,empty:empty}", null));
    assertEquals("empty", format(messageSupport, "%{c,null:null,empty:empty}", new int[0]));
    assertEquals("empty", format(messageSupport, "%{c,null:null,empty:empty}", Set.of()));
  }


  @Test
  @DisplayName("Elements formatted as empty text")
  void emptyElements()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("[]", format(messageSupport, "%{c}", new String[] { null, "" }));
    assertEquals("[]", format(messageSupport, "%{c}", Arrays.asList(null, "")));
  }


  @Test
  @DisplayName("Spaces around the wrapped value")
  void spaces()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("a [1, 2] b", format(messageSupport, "a %{c} b", new int[] { 1, 2 }));
    assertEquals("a[1, 2]b", format(messageSupport, "a%{c}b", List.of(1, 2)));
    assertEquals("a b", format(messageSupport, "a %{c} b", null));
  }


  @Test
  @DisplayName("Separator spaces")
  void separator()
  {
    final var messageSupport = createMessageSupport();

    assertEquals("[1, 2, 3, 4 and 5]",
        format(messageSupport, "%{c,list-sep:', ',list-sep-last:' and '}", new int[] { 1, 2, 3, 4, 5 }));
    assertEquals("[1.2.3.4.5]", format(messageSupport, "%{c,list-sep:'.'}", List.of(1, 2, 3, 4, 5)));
  }


  @Test
  @DisplayName("List value format")
  void listValue()
  {
    final var formatterService = createFormatterService();
    formatterService.addFormatter(new NumberFormatter());

    final var messageSupport = MessageSupportFactory.create(formatterService).setLocale("en-US");

    assertEquals("[01, -07, 248]",
        format(messageSupport, "%{c,list-value:'%{value,number:\"##00\"}'}", new int[] { 1, -7, 248 }));
    assertEquals("[<1>, <2>]", format(messageSupport, "%{c,list-value:'<%{value}>'}", List.of(1, 2)));
  }


  private static Stream<Arguments> crossTableParameters()
  {
    return Stream.of(
        Arguments.of("Empty array with max size 0", new String[0], null, 0, null, "[]"),
        Arguments.of("Array with last separator", new String[] { "A", "B", "C" }, " and ", null, null, "[A, B and C]"),
        Arguments.of("Array with max size 2 and more value", new String[] { "A", "B", "C" }, null, 2, "...", "[A, B, ...]"),
        Arguments.of("Array with max size 2 and last separator", new String[] { "A", "B", "C" }, " and ", 2, null, "[A and B]"),
        Arguments.of("Non-empty array with max size 0", new String[] { "A", "B", "C" }, null, 0, null, "[]"),
        Arguments.of("Non-empty array with max size 0 and more value", new String[] { "A", "B", "C" }, null, 0, "...", "[...]"),
        Arguments.of("List with last separator", List.of("A", "B", "C"), " and ", null, null, "[A, B and C]"),
        Arguments.of("List with max size 2 and more value", List.of("A", "B", "C"), null, 2, "...", "[A, B, ...]"),
        Arguments.of("List with max size 1", List.of("A", "B", "C"), null, 1, null, "[A]"),
        Arguments.of("List with empty element and max size 2", Arrays.asList("A", "", "C", "D"), null, 2, null, "[A, C]")
    );
  }


  @DisplayName("Max size, last separator and more value")
  @ParameterizedTest(name = "{0}")
  @MethodSource("crossTableParameters")
  void crossTable(@NotNull String name, Object value, String listSepLast, Integer listMaxSize, String listValueMore,
                  @NotNull String result)
  {
    final var messageFormat = new StringBuilder("%{c");
    if (listSepLast != null)
      messageFormat.append(",list-sep-last:\"").append(listSepLast).append('"');
    if (listMaxSize != null)
      messageFormat.append(",list-max-size:").append(listMaxSize);
    if (listValueMore != null)
      messageFormat.append(",list-value-more:\"").append(listValueMore).append('"');
    messageFormat.append('}');

    assertEquals(result, format(createMessageSupport(), messageFormat.toString(), value));
  }


  @Contract(pure = true)
  private static @NotNull GenericFormatterService createFormatterService()
  {
    final var formatterService = new GenericFormatterService();

    final var arrayFormatter = new ArrayFormatter();
    formatterService.addFormatter(arrayFormatter);
    formatterService.addFormatterForType(new FormattableType(char[].class), arrayFormatter);
    formatterService.addFormatter(new IterableFormatter());
    formatterService.addFormatter(new WrapIterableOrArrayFormatter());

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
