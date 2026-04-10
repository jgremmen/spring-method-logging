module de.sayayi.lib.methodlogging
{
  requires de.sayayi.lib.message;
  requires spring.aop;
  requires spring.beans;
  requires spring.context;
  requires spring.core;

  requires static java.logging;
  requires static ch.qos.logback.classic;
  requires static ch.qos.logback.core;
  requires static org.apache.logging.log4j;
  requires static org.jboss.logging;
  requires static org.jetbrains.annotations;
  requires static org.slf4j;
  requires static spring.jcl;

  exports de.sayayi.lib.methodlogging;
  exports de.sayayi.lib.methodlogging.annotation;
  exports de.sayayi.lib.methodlogging.logger;
}