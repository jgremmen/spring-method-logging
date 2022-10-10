package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static lombok.AccessLevel.PRIVATE;


@RequiredArgsConstructor(access = PRIVATE)
@SuppressWarnings("DuplicatedCode")
public final class Slf4JMethodLogger implements MethodLogger
{
  public static final @NotNull MethodLoggerFactory FIELD_FACTORY = Slf4JMethodLogger::from;

  private final Logger logger;


  @Override
  public void log(@NotNull Level level, String message)
  {
    switch(level)
    {
      case TRACE:
        logger.trace(message);
        break;

      case DEBUG:
        logger.debug(message);
        break;

      case INFO:
        logger.info(message);
        break;
    }
  }


  @Override
  public boolean isLogEnabled(@NotNull Level level)
  {
    switch(level)
    {
      case TRACE:
        return logger.isTraceEnabled();

      case DEBUG:
        return logger.isDebugEnabled();

      case INFO:
        return logger.isInfoEnabled();
    }

    return false;
  }


  @Contract(pure = true)
  static @NotNull MethodLogger from(@NotNull Field loggerField, @NotNull Object instance)
  {
    try {
      return new Slf4JMethodLogger((Logger)loggerField.get(instance));
    } catch(IllegalAccessException e) {
      return NO_OP;
    }
  }
}