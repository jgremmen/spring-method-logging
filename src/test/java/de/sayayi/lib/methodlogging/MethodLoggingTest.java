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
import de.sayayi.lib.message.formatter.post.runtime.ClipPostFormatter;
import de.sayayi.lib.message.part.normalizer.LRUMessagePartNormalizer;
import de.sayayi.lib.methodlogging.annotation.EnableMethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLogging;
import de.sayayi.lib.methodlogging.annotation.MethodLoggingConfig;
import de.sayayi.lib.methodlogging.annotation.ParamLog;
import de.sayayi.lib.methodlogging.logger.JULLoggerFactory;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;
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
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static de.sayayi.lib.methodlogging.Level.DEBUG;
import static de.sayayi.lib.methodlogging.Visibility.HIDE;
import static de.sayayi.lib.methodlogging.Visibility.SHOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
  @Autowired private JULMultiLoggerBean julMultiLoggerBean;
  @Autowired private MethodLoggerFactoryDelegate methodLoggerFactoryDelegate;
  @Autowired private MyBean myBean;
  @Autowired private MyBeanIf myBeanIf;
  @Autowired private Supplier<String> mySupplier;
  @Autowired private Greeter greeter;
  @Autowired private Worker worker;


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
    assertTrue(log.get(0).matches("INFO\\|> get:\\d+"), log.get(0));
    assertEquals("DEBUG|result = Mr. Supplier", log.get(1));
    assertTrue(log.get(2).matches("INFO\\|< get:\\d+"), log.get(2));
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
  @DisplayName("JDK proxy uses parameters, line number and logger field of implementation")
  void testMethod_jdkProxy()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    assertTrue(Proxy.isProxyClass(greeter.getClass()));
    assertEquals("Hello bob", greeter.greet("bob"));
    assertTrue(factory.log.getFirst().matches("INFO\\|> greet\\(WHO=bob\\):\\d+"), factory.log.getFirst());
    assertEquals(GreeterImpl.class, factory.fields.getFirst().getDeclaringClass());
  }


  @Test
  @DisplayName("Logger field of target class is preferred over field of declaring class")
  void testMethod_inheritedMethodLoggerField()
  {
    val factory = new ListMethodLoggerFactory();
    methodLoggerFactoryDelegate.setFactory(factory);

    worker.work();

    assertEquals("INFO|> work", factory.log.getFirst());
    assertEquals(Worker.class, factory.fields.getFirst().getDeclaringClass());
  }


  @Test
  @DisplayName("Static logger fields of the same class are cached per field")
  void testStaticLoggerFieldPerMethod()
  {
    val logMessages = new ArrayList<String>();
    val auditMessages = new ArrayList<String>();
    val logHandler = new ListHandler(logMessages);
    val auditHandler = new ListHandler(auditMessages);

    JULMultiLoggerBean.log.addHandler(logHandler);
    JULMultiLoggerBean.auditLog.addHandler(auditHandler);
    try {
      methodLoggerFactoryDelegate.setFactory(new JULLoggerFactory(false));

      // the logger used first must not be used for the other method
      julMultiLoggerBean.audit();
      julMultiLoggerBean.test();
      julMultiLoggerBean.audit();
    } finally {
      JULMultiLoggerBean.log.removeHandler(logHandler);
      JULMultiLoggerBean.auditLog.removeHandler(auditHandler);
    }

    assertEquals(List.of("INFO|> test", "INFO|< test"), logMessages);
    assertEquals(List.of("INFO|> audit", "INFO|< audit", "INFO|> audit", "INFO|< audit"), auditMessages);
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
    public void setWithParam(@SuppressWarnings("unused") @ParamLog("%(clip,'%{value}',clip:8,clip-suffix-text:'...')") String name) {
    }


    @MethodLogging
    @SuppressWarnings("unused")
    public void setWithMultipleParams(@ParamLog(name = "id") int p0,
                                      @ParamLog(inline = false, name = "name") String p1) {
    }
  }




  public interface Greeter {
    String greet(String who);
  }




  public static class GreeterImpl implements Greeter
  {
    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger("greeter");


    @Override
    @MethodLogging(result = HIDE)
    public String greet(@ParamLog(name = "WHO") String who) {
      return "Hello " + who;
    }
  }




  public abstract static class AbstractWorker
  {
    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger("abstract-worker");


    @MethodLogging(lineNumber = HIDE)
    public void work() {
    }
  }




  public static class Worker extends AbstractWorker
  {
    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger("worker");
  }




  @MethodLoggingConfig(lineNumber = HIDE)
  public interface MyBeanIf
  {
    @MethodLogging(resultFormat = "nameIf = %{result}")
    String getName();
  }




  @Component
  @MethodLoggingConfig(lineNumber = HIDE)
  public static class JULMultiLoggerBean
  {
    private static final Logger log = Logger.getLogger("multi-logger.log");
    private static final Logger auditLog = Logger.getLogger("multi-logger.audit");


    @MethodLogging
    public void test() {
    }


    @MethodLogging(loggerFieldName = "auditLog")
    public void audit() {
    }
  }




  @RequiredArgsConstructor
  private static final class ListHandler extends Handler
  {
    private final List<String> messages;


    @Override
    public void publish(LogRecord record) {
      messages.add(record.getLevel() + "|" + record.getMessage());
    }


    @Override
    public void flush() {
    }


    @Override
    public void close() {
    }
  }




  @Configuration
  @EnableMethodLogging
  @Import({ MyBean.class, JULMultiLoggerBean.class })
  static class MyConfiguration implements MethodLoggingConfigurer
  {
    @Override
    public MessageSupport messageSupport()
    {
      val formatterService = new GenericFormatterService();

      formatterService.addPostFormatter(new ClipPostFormatter());

      return MessageSupportFactory.create(formatterService,
          new MessageFactory(LRUMessagePartNormalizer.create(64)));
    }


    @Bean
    @Override
    public MethodLoggerFactoryDelegate methodLoggerFactory() {
      return new MethodLoggerFactoryDelegate();
    }


    @Bean
    public Greeter greeter() {
      return new GreeterImpl();
    }


    @Bean
    public Worker worker() {
      return new Worker();
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
      return new Supplier<>() {
        @Override
        @MethodLogging
        public String get() {
          return "Mr. Supplier";
        }
      };
    }
  }




  @Setter
  static final class MethodLoggerFactoryDelegate implements MethodLoggerFactory
  {
    @Delegate private MethodLoggerFactory factory;


    @Override
    public void validate(Field loggerField, @NotNull Class<?> type) {
      // the factory is set by the tests after the definitions have been analyzed
    }
  }




  public static final class ListMethodLoggerFactory implements MethodLoggerFactory
  {
    final List<String> log = new ArrayList<>();
    final List<Field> fields = new ArrayList<>();


    @Override
    public @NotNull MethodLogger from(Field loggerField, @NotNull Object obj, @NotNull Class<?> type)
    {
      fields.add(loggerField);

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
