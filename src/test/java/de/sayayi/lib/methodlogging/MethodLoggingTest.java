package de.sayayi.lib.methodlogging;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.logger.GenericMethodLoggerFactory;
import de.sayayi.lib.methodlogging.logger.JULMethodLogger;
import lombok.extern.java.Log;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static org.junit.jupiter.api.Assertions.assertEquals;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    MethodLoggingTest.MyConfiguration.class,
    MethodLoggingTest.MyBean.class
})
public class MethodLoggingTest
{
  @Autowired private MethodLoggerFactoryDelegate methodLoggerFactoryDelegate;
  @Autowired private MyBean myBean;
  @Autowired private JULLoggerBean julLoggerBean;


  @Test
  void testMethod()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    myBean.getName();

    assertEquals("INFO|> getName", factory.log.get(0));
    assertEquals("DEBUG|name = Mr. Bean", factory.log.get(1));
    assertEquals("INFO|< getName", factory.log.get(2));
  }


  @Test
  void testJULLogger()
  {
    methodLoggerFactoryDelegate.setFactory(new GenericMethodLoggerFactory());
    julLoggerBean.test();

    methodLoggerFactoryDelegate.setFactory(JULMethodLogger.FIELD_FACTORY);
    julLoggerBean.test();
  }




  @Component
  public static class MyBean
  {
    @MethodLogging(lineNumber = HIDE, resultFormat = "name = %{result}")
    public String getName() {
      return "Mr. Bean";
    }


    @MethodLogging
    public void setName(String name) {
    }
  }




  @Component
  @Log
  @MethodLoggingConfig(loggerFieldName = "log")
  public static class JULLoggerBean
  {
    @MethodLogging(lineNumber = HIDE)
    public void test() {
    }
  }




  @Configuration
  @EnableMethodLogging
  @Import({ MyBean.class, JULLoggerBean.class })
  public static class MyConfiguration implements MethodLoggingConfigurer
  {
    @Bean
    @Override
    public MethodLoggerFactoryDelegate methodLoggerFactory() {
      return new MethodLoggerFactoryDelegate();
    }
  }




  public static final class ListMethodLoggerFactory implements MethodLoggerFactory
  {
    final List<String> log = new ArrayList<>();


    @Override
    public @NotNull MethodLogger from(Field loggerField, Object obj)
    {
      return new MethodLogger() {
        @Override
        public void log(@NotNull Level level, String message) {
          log.add(level.name() + '|' + message);
        }

        @Override
        public boolean isLogEnabled(@NotNull Level level) {
          return true;
        }
      };
    }
  }
}