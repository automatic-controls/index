import com.controlj.green.addonsupport.access.node.*;
public class RevertAction {
  private volatile Node node;
  private volatile String value;
  private volatile Message m;
  public RevertAction(Node node, String value, Message m){
    this.node = node;
    this.value = value;
    this.m = m;
  }
  public void revert(Display d) throws Exception {
    try{
      Message msg = new Message(m.getType(), "Reverted ("+m.getDescription()+')', m.getPath(), m.getLink());
      String current = node.getValue();
      if (!current.equals(value)){
        node.setValue(value);
        d.reverts.add(new RevertAction(node,current,msg));
      }
      d.add(msg);
    }catch(Exception e){
      d.add(MessageType.ERROR, "Failure ("+m.getDescription()+')', m.getPath(), m.getLink());
    }
  }
}