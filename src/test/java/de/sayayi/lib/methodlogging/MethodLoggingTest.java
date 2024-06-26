/*
 * Copyright 2022 Jeroen Gremmen
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

import de.sayayi.lib.message.MessageFactory;
import de.sayayi.lib.message.MessageSupport;
import de.sayayi.lib.message.MessageSupportFactory;
import de.sayayi.lib.message.formatter.GenericFormatterService;
import de.sayayi.lib.message.formatter.named.ClipFormatter;
import de.sayayi.lib.message.parser.normalizer.LRUMessagePartNormalizer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import de.sayayi.lib.methodlogging.logger.JULLoggerFactory;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.java.Log;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.annotation.MethodLogging.Visibility.SHOW;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.context.annotation.ScopedProxyMode.INTERFACES;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    MethodLoggingTest.MyConfiguration.class,
    MethodLoggingTest.MyBean.class
})
@DisplayName("Method logging")
public class MethodLoggingTest
{
  @Autowired private JULLoggerBean julLoggerBean;
  @Autowired private MethodLoggerFactoryDelegate methodLoggerFactoryDelegate;
  @Autowired private MyBean myBean;
  @Autowired private MyBeanIf myBeanIf;
  @Autowired private Supplier<String> mySupplier;


  @Test
  @DisplayName("Bean method without parameters")
  void beanMethodNoParameters()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    val log = factory.log;

    myBean.getName();

    assertEquals(3, log.size());
    assertEquals("INFO|> getName", log.get(0));
    assertEquals("DEBUG|name = Mr. Bean", log.get(1));
    assertEquals("INFO|< getName", log.get(2));
  }


  @Test
  @DisplayName("Interface method with annotation on interface")
  void interfaceLogging()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    val log = factory.log;

    myBeanIf.getName();

    assertEquals(3, log.size());
    assertEquals("INFO|> getName", log.get(0));
    assertEquals("DEBUG|nameIf = Mr. Bean", log.get(1));
    assertEquals("INFO|< getName", log.get(2));
  }


  @Test
  @DisplayName("Interface method with annotation on implementation")
  void supplierGet()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    val log = factory.log;

    mySupplier.get();

    assertEquals(3, log.size());
    assertEquals("INFO|> get", log.get(0));
    assertEquals("DEBUG|result = Mr. Supplier", log.get(1));
    assertEquals("INFO|< get", log.get(2));
  }


  @Test
  @DisplayName("Single parameter method with line numbers")
  void testMethod_setWithParam()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    myBean.setWithParam("This is a very long text");

    assertTrue(factory.log.get(0).startsWith("DEBUG|> setWithParam(name=This...):"));
    assertTrue(factory.log.get(1).startsWith("DEBUG|< setWithParam:"));
  }


  @Test
  @DisplayName("Multiple parameter method with in-method logging")
  void testMethod_setWithMultipleParams()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    myBean.setWithMultipleParams(45, "Mr. Bean");

    assertEquals("INFO|> setWithMultipleParams(id=45)", factory.log.get(0));
    assertEquals("DEBUG|parameter 'name' = Mr. Bean", factory.log.get(1));
    assertEquals("INFO|< setWithMultipleParams", factory.log.get(2));
  }


  @Test
  @DisplayName("Excluding method parameters")
  void testMethod_excludeParams()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    myBean.excludeParams(12, -45,"Mr. Bean", Locale.UK);

    assertEquals("INFO|> excludeParams(name=Mr. Bean,locale=en_GB)", factory.log.get(0));
    assertEquals("INFO|< excludeParams", factory.log.get(1));
  }


  @Test
  @DisplayName("Exception handling")
  void testMethod_exception()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    assertEquals("314", assertThrowsExactly(IllegalArgumentException.class,
        () -> myBean.exception(314)).getMessage());
    assertEquals("INFO|> exception(id=314)", factory.log.get(0));
    assertEquals("INFO|< exception -> IllegalArgumentException(314)", factory.log.get(1));
  }


  @Test
  @DisplayName("java.util.logging logger factory")
  void testJULLogger()
  {
    methodLoggerFactoryDelegate.setFactory(new JULLoggerFactory(false));
    julLoggerBean.test();
  }




  @Component
  @MethodLoggingConfig(lineNumber = HIDE)
  public static class MyBean
  {
    @MethodLogging(resultFormat = "name = %{result}")
    public String getName() {
      return "Mr. Bean";
    }


    @MethodLogging(lineNumber = SHOW, entryExitLevel = DEBUG)
    public void setWithParam(@SuppressWarnings("unused") @ParamLog("%{value,clip,clip-size:8}") String name) {
    }


    @MethodLogging
    @SuppressWarnings("unused")
    public void setWithMultipleParams(@ParamLog(name = "id") int p0,
                                      @ParamLog(inline = false, name = "name") String p1) {
    }


    @MethodLogging(exclude = { "id", "p1" })
    @SuppressWarnings("unused")
    public void excludeParams(@ParamLog(name = "id") int p0, int p1, String name, Locale locale) {
    }


    @MethodLogging
    public void exception(int id) {
      throw new IllegalArgumentException(Integer.toString(id));
    }
  }




  @MethodLoggingConfig(lineNumber = HIDE)
  public interface MyBeanIf
  {
    @MethodLogging(resultFormat = "nameIf = %{result}")
    String getName();
  }




  @Component
  @Log
  public static class JULLoggerBean
  {
    @MethodLogging
    public void test() {
    }
  }




  @Configuration
  @EnableMethodLogging
  @Import({ MyBean.class, JULLoggerBean.class })
  static class MyConfiguration implements MethodLoggingConfigurer
  {
    @Override
    public MessageSupport messageSupport()
    {
      val formatterService = new GenericFormatterService();

      formatterService.addFormatter(new ClipFormatter());

      return MessageSupportFactory.create(formatterService,
          new MessageFactory(new LRUMessagePartNormalizer(64)));
    }


    @Bean
    @Override
    public MethodLoggerFactoryDelegate methodLoggerFactory() {
      return new MethodLoggerFactoryDelegate();
    }


    @Bean
    @Scope(proxyMode = INTERFACES)
    public MyBeanIf myBeanIf() {
      return  () -> "Mr. Bean";
    }


    @Bean
    @Scope(proxyMode = INTERFACES)
    public Supplier<String> mySupplier()
    {
      return new Supplier<String>() {
        @Override
        @MethodLogging
        public String get() {
          return "Mr. Supplier";
        }
      };
    }
  }




  @Setter
  static final class MethodLoggerFactoryDelegate implements MethodLoggerFactory {
    @Delegate private MethodLoggerFactory factory;
  }




  public static final class ListMethodLoggerFactory implements MethodLoggerFactory
  {
    final List<String> log = new ArrayList<>();


    @Override
    public @NotNull MethodLogger from(Field loggerField, @NotNull Object obj, @NotNull Class<?> type)
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
