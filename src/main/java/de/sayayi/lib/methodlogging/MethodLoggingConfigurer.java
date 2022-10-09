package de.sayayi.lib.methodlogging;

import de.sayayi.lib.message.MessageContext;
import org.jetbrains.annotations.Contract;


public interface MethodLoggingConfigurer
{
  @Contract(pure = true)
  default MessageContext messageContext() {
    return null;
  }


  @Contract(pure = true)
  default MethodLoggerFactory methodLoggerFactory() {
    return null;
  }
}