public enum MessageType {
  /** Controller Communication */
  COM("Controller Communication"),
  /** SCHEDULES */
  SCHEDULE("Schedules"),
  /** I/O Points */
  IO("I/O Points"),
  /** Network Points */
  NETWORK("Network Points"),
  /** Trend Sources */
  TREND("Trend Sources"),
  /** Alarm Sources */
  ALARM("Alarm Sources"),
  /** Exceptions */
  ERROR("Exceptions");
  private String name;
  private MessageType(String name){
    this.name = name;
  }
  @Override public String toString(){
    return name;
  }
}