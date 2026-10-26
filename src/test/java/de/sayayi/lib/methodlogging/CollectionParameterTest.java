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
package de.sayayi.lib.methodlogging;

import de.sayayi.lib.methodlogging.MethodLoggingTest.ListMethodLoggerFactory;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.IntStream;

import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Tests the formatting of array, list and set parameters and results with the default message support.
 *
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CollectionParameterTest.Config.class)
@DisplayName("Collection parameters with the default message support")
class CollectionParameterTest
{
  private static final ListMethodLoggerFactory FACTORY = new ListMethodLoggerFactory();

  @Autowired private CollectionBean bean;


  @BeforeEach
  void setup() {
    FACTORY.log.clear();
  }


  @Test
  @DisplayName("Primitive arrays")
  void primitiveArrays()
  {
    bean.primitiveArrays(new boolean[] { true, false }, new int[] { 12, -7, 99 }, new long[] { 5 },
        new char[] { 'a', 'b' });

    assertEquals(List.of(
        "INFO|> primitiveArrays(flags=[true, false],ints=[12, -7, 99],longs=[5],chars=[a, b])",
        "INFO|< primitiveArrays"), FACTORY.log);
  }


  @Test
  @DisplayName("Object array")
  void objectArray()
  {
    bean.objectArray(new String[] { "Dune", null, "Emma" });

    assertEquals(List.of(
        "INFO|> objectArray(titles=[Dune, Emma])",
        "INFO|< objectArray"), FACTORY.log);
  }


  @Test
  @DisplayName("List and set")
  void listAndSet()
  {
    bean.listAndSet(List.of("Dune", "Emma"), new LinkedHashSet<>(List.of(3, 1, 2)));

    assertEquals(List.of(
        "INFO|> listAndSet(titles=[Dune, Emma],ids=[3, 1, 2])",
        "INFO|< listAndSet"), FACTORY.log);
  }


  @Test
  @DisplayName("Nested arrays and lists")
  void nested()
  {
    bean.nested(List.of(List.of(1, 2), List.of(3)), new int[][] { { 4, 5 }, {} });

    assertEquals(List.of(
        "INFO|> nested(lists=[[1, 2], [3]],arrays=[[4, 5], []])",
        "INFO|< nested"), FACTORY.log);
  }


  @Test
  @DisplayName("Empty and null array, list and set")
  void emptyAndNull()
  {
    bean.emptyAndNull(new int[0], List.of(), Set.of(), null);

    assertEquals(List.of(
        "INFO|> emptyAndNull(array=[],list=[],set=[],nothing=)",
        "INFO|< emptyAndNull"), FACTORY.log);
  }


  @Test
  @DisplayName("Elements beyond the maximum list size are abbreviated")
  @Disabled("Disabled due to a bug in message format")
  void maxSize()
  {
    val values = IntStream.rangeClosed(1, 25).toArray();
    val expected = "[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, …]";

    bean.maxSize(values, Arrays.stream(values).boxed().toList());

    assertEquals(List.of(
        "INFO|> maxSize(array=" + expected + ",list=" + expected + ')',
        "INFO|< maxSize"), FACTORY.log);
  }


  @Test
  @DisplayName("Separately logged parameter and result")
  void separateParameterAndResult()
  {
    bean.separateParameterAndResult(new ArrayList<>(List.of("Dune", "Emma")));

    assertEquals(List.of(
        "INFO|> separateParameterAndResult",
        "DEBUG|parameter 'titles' = [Dune, Emma]",
        "DEBUG|result = [2, 1]",
        "INFO|< separateParameterAndResult"), FACTORY.log);
  }


  @Test
  @DisplayName("Parameter format with list configuration")
  @Disabled("Disabled due to a bug in message format")
  void parameterFormat()
  {
    bean.parameterFormat(List.of(1, 2, 3, 4));

    assertEquals(List.of(
        "INFO|> parameterFormat(ids=[1, 2, and more])",
        "INFO|< parameterFormat"), FACTORY.log);
  }




  @SuppressWarnings({ "unused", "UnusedReturnValue" })
  public static class CollectionBean
  {
    @MethodLogging(lineNumber = HIDE)
    public void primitiveArrays(boolean[] flags, int[] ints, long[] longs, char[] chars) {
    }


    @MethodLogging(lineNumber = HIDE)
    public void objectArray(String[] titles) {
    }


    @MethodLogging(lineNumber = HIDE)
    public void listAndSet(List<String> titles, Set<Integer> ids) {
    }


    @MethodLogging(lineNumber = HIDE)
    public void nested(List<List<Integer>> lists, int[][] arrays) {
    }


    @MethodLogging(lineNumber = HIDE)
    public void emptyAndNull(int[] array, List<String> list, Set<String> set, List<String> nothing) {
    }


    @MethodLogging(lineNumber = HIDE)
    public void maxSize(int[] array, List<Integer> list) {
    }


    @MethodLogging(lineNumber = HIDE)
    public int[] separateParameterAndResult(@ParamLog(inline = false) List<String> titles) {
      return new int[] { titles.size(), 1 };
    }


    @MethodLogging(lineNumber = HIDE)
    public void parameterFormat(
        @ParamLog("%{value,list-max-size:2,list-value-more:'and more'}") List<Integer> ids) {
    }
  }




  @Configuration
  @EnableMethodLogging
  @Import(CollectionBean.class)
  static class Config implements MethodLoggingConfigurer
  {
    @Override
    public MethodLoggerFactory methodLoggerFactory() {
      return FACTORY;
    }
  }
}
