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
import de.sayayi.lib.methodlogging.MethodLoggingConfigurer;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Lazy message creation")
class LazyMessageTest
{
  private static final int THREADS = 16;


  @RepeatedTest(10)
  @DisplayName("Concurrently requested messages are created once")
  void concurrentCreation() throws Exception
  {
    final var methodDef = requireNonNull(
        new AnnotationMethodLoggingSource(
            new MethodLoggingConfigurer() {},
            new DefaultResourceLoader())
            .getMethodDefinition(Bean.class.getMethod("logged", String.class, String.class), Bean.class));

    assertEquals(1, methodDef.inlineParameters.size());
    assertEquals(1, methodDef.inMethodParameters.size());

    assertCreatedOnce(methodDef.inlineParameterMessage);
    assertCreatedOnce(methodDef.parameterMessage);
    assertCreatedOnce(methodDef.resultMessage);
    assertCreatedOnce(methodDef.inlineParameters.getFirst().formatMessage());
    assertCreatedOnce(methodDef.inMethodParameters.getFirst().formatMessage());
  }


  private static void assertCreatedOnce(@NotNull Supplier<Message> message) throws Exception
  {
    final var barrier = new CyclicBarrier(THREADS);
    final Callable<Message> task = () -> {
      barrier.await();
      return message.get();
    };

    try(var executor = Executors.newFixedThreadPool(THREADS)) {
      final var futures = new ArrayList<Future<Message>>();

      for(int n = 0; n < THREADS; n++)
        futures.add(executor.submit(task));

      final var first = requireNonNull(futures.getFirst().get());

      for(var future: futures)
        assertSame(first, future.get());

      // still the same message, once created
      assertSame(first, message.get());
    }
  }




  public static class Bean
  {
    @MethodLogging
    @SuppressWarnings({ "unused", "SameReturnValue" })
    public String logged(String inline, @ParamLog(inline = false) String inMethod) {
      return "result";
    }
  }
}
