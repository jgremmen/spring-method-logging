package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import static lombok.AccessLevel.PRIVATE;


@RequiredArgsConstructor(access = PRIVATE)
@SuppressWarnings("DuplicatedCode")
public final class JULMethodLogger implements MethodLogger
{
  public static final @NotNull MethodLoggerFactory FIELD_FACTORY = Log4J2MethodLogger::from;

  private static final java.util.logging.Level[] LEVELS = new java.util.logging.Level[] {
      FINEST, FINE, INFO
  };

  private final Logger logger;


  @Override
  public void log(@NotNull Level level, String message) {
    logger.log(LEVELS[level.ordinal()], message);
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level) {
    return logger.isLoggable(LEVELS[level.ordinal()]);
  }


  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new JULMethodLogger((Logger)loggerField.get(instance));
    } catch (IllegalAccessException e) {
      return NO_OP;
    }
  }
}