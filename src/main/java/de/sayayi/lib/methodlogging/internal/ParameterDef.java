package de.sayayi.lib.methodlogging.internal;

import de.sayayi.lib.message.Message;
import de.sayayi.lib.message.MessageContext;
import lombok.NoArgsConstructor;
import lombok.Synchronized;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

import static lombok.AccessLevel.PACKAGE;


@NoArgsConstructor(access = PACKAGE)
final class ParameterDef implements Serializable
{
  int index;
  String name;
  String format;
  boolean inMethod;

  Message formatMessage;


  @Synchronized
  @NotNull Message getFormatMessage(@NotNull MessageContext messageContext)
  {
    if (formatMessage == null)
      formatMessage = messageContext.getMessageFactory().parse(format).trim();

    return formatMessage;
  }
}