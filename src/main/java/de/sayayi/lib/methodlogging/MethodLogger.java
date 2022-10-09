package de.sayayi.lib.methodlogging;

import de.sayayi.lib.methodlogging.annotation.MethodLogging.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


public interface MethodLogger
{
  @NotNull MethodLogger NO_OP = new MethodLogger() {
    @Override public void log(@NotNull Level level, String message) {}
    @Override public boolean isLogEnabled(@NotNull Level level) { return false; }
  };


  void log(@NotNull Level level, String message);


  @Contract(pure = true)
  boolean isLogEnabled(@NotNull Level level);
}