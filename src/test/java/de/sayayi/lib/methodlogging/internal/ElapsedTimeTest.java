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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static de.sayayi.lib.methodlogging.internal.MethodLoggingInterceptor.logMethodExit_elapsed;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Jeroen Gremmen
 * @since 0.6.0
 */
@DisplayName("Elapsed time formatting")
class ElapsedTimeTest
{
  @ParameterizedTest(name = "{0} ms -> {1}", quoteTextArguments = false)
  @CsvSource({
      // below one minute
      "0,         0ms",
      "1,         1ms",
      "999,       999ms",
      "1000,      1s",
      "1001,      1s1ms",
      "59999,     59s999ms",
      // below one hour
      "60000,     1m",
      "60001,     1m",
      "60999,     1m",
      "61000,     1m1s",
      "61001,     1m1s",
      "3599999,   59m59s",
      // one hour and more, rounded to minutes
      "3600000,   1h",
      "3600001,   1h",
      "3629999,   1h",
      "3630000,   1h1m",
      "3640000,   1h1m",
      "3660000,   1h1m",
      "7170000,   2h",  // 1h59m30s
      "219600000, 61h",
      "219660000, 61h1m"
  })
  @DisplayName("Elapsed time")
  void elapsedTime(long millis, String expected)
  {
    assertEquals(expected, logMethodExit_elapsed(millis));
  }
}
