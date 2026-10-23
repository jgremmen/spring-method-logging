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
package de.sayayi.lib.methodlogging.logger;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.INFO;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.5.1
 */
@DisplayName("Location aware method loggers")
class LocationAwareLoggerTest
{
  private static final StackTraceElement LOCATION =
      new StackTraceElement("com.example.Service", "doWork", "Service.java", 42);


  @Test
  @DisplayName("JUL logger reports class and method of the location")
  void jul()
  {
    final var records = new ArrayList<LogRecord>();
    final var logger = Logger.getLogger("location-aware-test");
    final var handler = new Handler() {
      @Override public void publish(LogRecord record) { records.add(record); }
      @Override public void flush() {}
      @Override public void close() {}
    };

    logger.addHandler(handler);
    try {
      new JULLogger(logger).log(INFO, "> doWork", LOCATION);
    } finally {
      logger.removeHandler(handler);
    }

    assertEquals(1, records.size());
    assertEquals("> doWork", records.getFirst().getMessage());
    assertEquals("com.example.Service", records.getFirst().getSourceClassName());
    assertEquals("doWork", records.getFirst().getSourceMethodName());
  }


  @Test
  @DisplayName("Log4j2 logger passes the location")
  void log4j2()
  {
    final var logger = new CapturingLog4j2Logger();

    new Log4j2Logger(logger).log(INFO, "> doWork", LOCATION);

    assertEquals(List.of("> doWork"), logger.messages);
    assertEquals(List.of(LOCATION), logger.locations);
  }


  @Test
  @DisplayName("Logback logger passes the location as caller data")
  void logback()
  {
    final var loggerContext = new LoggerContext();
    final var logger = loggerContext.getLogger("location-aware-test");
    final var appender = new ListAppender<ILoggingEvent>();

    appender.setContext(loggerContext);
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(ch.qos.logback.classic.Level.INFO);

    final var logbackLogger = new LogbackLogger(logger);
    logbackLogger.log(INFO, "> doWork", LOCATION);
    logbackLogger.log(DEBUG, "not logged", LOCATION);

    assertEquals(1, appender.list.size());

    final var event = appender.list.getFirst();
    assertEquals("> doWork", event.getFormattedMessage());
    assertEquals(ch.qos.logback.classic.Level.INFO, event.getLevel());
    assertArrayEquals(new StackTraceElement[] { LOCATION }, event.getCallerData());
  }




  /**
   * Log4j2 logger capturing the messages and locations passed by a log builder.
   */
  private static final class CapturingLog4j2Logger extends ExtendedLoggerWrapper
  {
    private final List<String> messages = new ArrayList<>();
    private final List<StackTraceElement> locations = new ArrayList<>();


    private CapturingLog4j2Logger() {
      super(StatusLogger.getLogger(), "location-aware-test", null);
    }


    @Override
    public boolean isEnabled(org.apache.logging.log4j.Level level) {
      return true;
    }


    @Override
    public boolean isEnabled(org.apache.logging.log4j.Level level, Marker marker, String message, Throwable t) {
      return true;
    }


    @Override
    public void logMessage(org.apache.logging.log4j.Level level, Marker marker, String fqcn,
                           StackTraceElement location, Message message, Throwable throwable)
    {
      messages.add(message.getFormattedMessage());
      locations.add(location);
    }
  }
}
