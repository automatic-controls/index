abstract class Task {
  public volatile Display d = null;
  /**
   * This method is meant to be invoked many times.
   * Each invokation should take less than one second, and should complete a small subtask.
   * @return {@code true} if the task is finished; {@code false} otherwise.
   */
  public abstract boolean run() throws Exception;
}