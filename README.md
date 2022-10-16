## Spring Method Logging Extension

Add method entry, exit, parameter and result logging capabilities to spring managed beans.
All it requires is a couple of annotations.

### Getting started

The method logging functionality must be enabled by providing the <code>@EnableMethodLogging</code> 
on a spring configuration bean.

    @Configuration
    @EnableMethodLogging
    public class LetsDoSomeLoggingConfiguration {
    }

Now each bean method that has the <code>@MethodLogging</code> annotation will have its entry and exit logged.

    @Component
    public class MyBean
    {
      @MethodLogging(lineNumber = HIDE)
      public String test(String name) {
        return name;
      }
    }

Invoking method test will generate the following logging output:

    > test(name=Hello World)
    result = Hello World
    < test

### Using Logger Instance

The basic configuration will use the jcl logger also used by the spring framework, which in turn will redirect to
another logging framework like eg. Log4j or Slf4j. Method Logging can be configured to use a logger field provided 
by the instance that contains the methods to be logged.

A typical scenario for Log4j will look like this:

    public class MyBean 
    {
      // log4j logger
      private static final Logger logger = LogManager.getLogger(MyBean.class);

      @MethodLogging
      public void test() 
      {
        logger.info("I'm busy testing...");
        ...
        logger.info("not done yet");
        ...
        logger.info("I'm done");
      }
    }

Now the <code>logger</code> instance can be used for logging, provided the method logger factory is configured 
to use the Log4j framework and the correct field name. 

    @Configuration
    @EnableMethodLogging
    public class LetsDoSomeLoggingConfiguration implements MethodLoggingConfigurer 
    {
       public MethodLoggerFactory methodLoggerFactory() {
         return new Log4j2LoggerFactory(true);
       }

       public String defaultLoggerFieldName() {
         return "logger";
       }
    }