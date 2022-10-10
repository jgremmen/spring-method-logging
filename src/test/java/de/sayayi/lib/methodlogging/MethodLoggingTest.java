package de.sayayi.lib.methodlogging;

import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    MethodLoggingTest.MyConfiguration.class,
    MethodLoggingTest.MyBean.class
})
public class MethodLoggingTest
{
  @Autowired private MyBean myBean;
  @Autowired @Qualifier("log") private List<String> log;


  @Test
  void testMethod()
  {
    log.clear();
    myBean.getName();

    assertEquals("INFO|> getName", log.get(0));
    assertEquals("DEBUG|name = Mr. Bean", log.get(1));
    assertEquals("INFO|< getName", log.get(2));
  }


  @Configuration
  @EnableMethodLogging
  public static class MyConfiguration implements MethodLoggingConfigurer
  {
    @Override
    public MethodLoggerFactory methodLoggerFactory()
    {
      val log = log();

      return (loggerField, obj) -> new MethodLogger() {
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


    @Bean
    public List<String> log() {
      return new ArrayList<>();
    }


    @Bean
    public MyBean myBean() {
      return new MyBean();
    }
  }




  public static class MyBean
  {
    @MethodLogging(withLineNumber = false, resultFormat = "name = %{result}")
    public String getName() {
      return "Mr. Bean";
    }


    @MethodLogging
    public void setName(String name) {
    }
  }
}