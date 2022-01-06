class Message implements Comparable<Message> {
  private MessageType type;
  private String desc;
  private String path;
  private String link;
  public Message(MessageType type, String desc, String path, String link){
    this.type = type;
    this.desc = desc;
    this.path = path;
    this.link = link;
  }
  public MessageType getType(){
    return type;
  }
  public String getDescription(){
    return desc;
  }
  public String getPath(){
    return path;
  }
  public String getLink(){
    return link;
  }
  @Override public int compareTo(Message m){
    return type.compareTo(m.type);
  }
}