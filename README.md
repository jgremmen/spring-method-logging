## Spring Method Logging Extension

Add method entry, exit, parameter and result logging capabilities to spring managed beans.
All it requires is a couple of annotations.

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