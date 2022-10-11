package de.sayayi.lib.methodlogging.formatter;

import de.sayayi.lib.message.MessageContext;
import de.sayayi.lib.message.MessageContext.Parameters;
import de.sayayi.lib.message.data.Data;
import de.sayayi.lib.message.formatter.NamedParameterFormatter;
import de.sayayi.lib.message.formatter.support.AbstractParameterFormatter;
import de.sayayi.lib.message.internal.part.MessagePart.Text;
import de.sayayi.lib.message.internal.part.TextPart;
import lombok.val;
import lombok.var;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static de.sayayi.lib.message.internal.part.MessagePartFactory.nullText;
import static java.util.Collections.emptySet;


public final class CutOffFormatter extends AbstractParameterFormatter implements NamedParameterFormatter
{
  @Override
  public @NotNull String getName() {
    return "cutoff";
  }


  @Override
  protected @NotNull Text formatValue(@NotNull MessageContext messageContext, Object value, String format,
                                      @NotNull Parameters parameters, Data data)
  {
    if (value == null)
      return nullText();

    val text = messageContext
        .getFormatter(value.getClass())
        .format(messageContext, value, null, parameters, data);

    var s = text.getText();
    if (s == null)
      return nullText();
    s = s.trim();

    val maxSize = (int)Math.max(
        getConfigValueNumber(messageContext, "cut-size", parameters, data, true, 64),
        8);

    return s.length() <= maxSize
        ? text
        : new TextPart(s.substring(0, maxSize - 3).trim() + "...", text.isSpaceBefore(), text.isSpaceAfter());
  }


  @Override
  public @NotNull Set<Class<?>> getFormattableTypes() {
    return emptySet();
  }
}