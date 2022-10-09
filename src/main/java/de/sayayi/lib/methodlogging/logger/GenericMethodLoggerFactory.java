package de.sayayi.lib.methodlogging.logger;

import de.sayayi.lib.methodlogging.MethodLogger;
import de.sayayi.lib.methodlogging.MethodLoggerFactory;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;


public class GenericMethodLoggerFactory implements MethodLoggerFactory
{
  private final Map<Field,LoggerType> fieldLoggerTypeMap = new ConcurrentHashMap<>();


  @Override
  public @NotNull MethodLogger from(Field loggerField, Object obj)
  {
    val loggerType = fieldLoggerTypeMap.computeIfAbsent(requireNonNull(loggerField), field -> {
      val loggerClassName = field.getType().getName();

      switch(loggerClassName)
      {
        case "java.util.logging.Logger":
          return LoggerType.JUL;

        case "org.apache.logging.log4j.Logger":
          return LoggerType.LOG4J2;

        case "org.slf4j.Logger":
          return LoggerType.SLF4J;
      }

      throw new IllegalStateException("unknown logger class: " + loggerClassName);
    });

    switch(loggerType)
    {
      case JUL:
        return JULMethodLogger.from(loggerField, obj);

      case LOG4J2:
        return Log4J2MethodLogger.from(loggerField, obj);

      case SLF4J:
        return Slf4JMethodLogger.from(loggerField, obj);

      default:
        throw new IllegalStateException("unknown logger type: " + loggerType);
    }
  }




  enum LoggerType
  {
    JUL,
    LOG4J2,
    SLF4J
  }
}