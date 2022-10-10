package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

import static lombok.AccessLevel.PRIVATE;


@RequiredArgsConstructor(access = PRIVATE)
@SuppressWarnings("DuplicatedCode")
public final class Log4J2MethodLogger implements MethodLogger
{
  public static final @NotNull MethodLoggerFactory FIELD_FACTORY = Log4J2MethodLogger::from;


  private static final org.apache.logging.log4j.Level[] LEVELS = new org.apache.logging.log4j.Level[] {
      org.apache.logging.log4j.Level.TRACE,
      org.apache.logging.log4j.Level.DEBUG,
      org.apache.logging.log4j.Level.INFO
  };

  private final Logger logger;


  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isEnabled(LEVELS[level.ordinal()]);
  }


  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new Log4J2MethodLogger((Logger)loggerField.get(instance));
    } catch(IllegalAccessException e) {
      return NO_OP;
    }
  }
}