import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import com.controlj.green.addonsupport.access.*;
/** This object expires an hour after the corresponding task has been completed. */
public class Display {
  private volatile ArrayList<Message> arr;
  public volatile ArrayList<RevertAction> reverts;
  public volatile int percentComplete = 0;
  public volatile boolean completed = false;
  public volatile Exception error = null;
  public volatile boolean stop = false;
  public volatile boolean modify = false;
  public volatile String username = "Unspecified";
  private volatile int token;
  private volatile long expiry;
  public final AtomicBoolean archived = new AtomicBoolean();
  public volatile String status = "Unknown";
  public volatile long startTime;
  private volatile boolean reverted = false;
  public volatile int reverterToken = -1;
  public volatile Path csv;
  public final Object fileLock = new Object();
  public Display(int token){
    startTime = System.currentTimeMillis();
    arr = new ArrayList<Message>();
    reverts = new ArrayList<RevertAction>();
    this.token = token;
    setCSV();
    update();
  }
  private Display(){
    archived.set(true);
    completed = true;
    arr = null;
    reverts = null;
    update();
  }
  private void setCSV(){
    csv = ProgressHandler.folder.resolve("changelog"+String.valueOf(token)+".csv");
  }
  public byte[] serialize(){
    byte[] usernameBytes = username.getBytes();
    byte[] statusBytes = status.getBytes();
    SerializationStream s = new SerializationStream(usernameBytes.length+statusBytes.length+21);
    s.write(token);
    s.write(startTime);
    s.write(modify);
    s.write(usernameBytes);
    s.write(statusBytes);
    return s.data;
  }
  public static Display deserialize(SerializationStream s) throws Exception {
    Display d = new Display();
    d.token = s.readInt();
    d.startTime = s.readLong();
    d.modify = s.readBoolean();
    d.username = s.readString();
    d.status = s.readString();
    d.setCSV();
    return d;
  }
  public void archive(){
    synchronized (fileLock){
      if (archived.compareAndSet(false,true)){
        setStatus();
        try{
          PrintWriter out = new PrintWriter(csv.toFile());
          printCSV(out,null,false);
          out.flush();
          out.close();
        }catch(Exception e){}
        arr = null;
        reverts = null;
      }
    }
  }
  public void setStatus(){
    if (completed){
      if (isReverted()){
        status = "Reverted ID = "+String.valueOf(reverterToken);
      }else if (stop){
        status = "Cancelled at "+String.valueOf(percentComplete)+'%';
      }else if (error!=null){
        status = "Completed With Error";
      }else{
        status = "Completed";
      }
    }else if (stop){
      status = "Cancelling...";
    }else{
      status = String.valueOf(percentComplete)+'%';
    }
  }
  public int getToken(){
    return token;
  }
  public void update(){
    expiry = System.currentTimeMillis()+3600000L;
  }
  public boolean isExpired(){
    return System.currentTimeMillis()>expiry;
  }
  public void sort(){
    arr.sort(null);
  }
  public void add(MessageType type, String desc, String path, String link){
    arr.add(new Message(type,desc,path,link));
  }
  public void add(Message m){
    arr.add(m);
  }
  public void printHTML(PrintWriter out, String token){
    out.println("<!DOCTYPE html><html><head>");
    out.println("<title>ACES Commissioner</title>");
    out.println("<style> td{padding:4px;border:solid 1px black;} tr:nth-child(odd){background-color:#dddddd;} table{border:solid 1px black;border-collapse:collapse;} </style>");
    out.println("</head><body>");
    out.println("<h1 style=\"text-align:center\">Results"+(stop?" (Cancelled)":"")+"</h1>");
    out.println("<a target=\"_blank\" href=\"/"+MainGUI.name+"/Downloader?token="+token+"\" download=\"changelog.csv\">Download Changelog</a>");
    if (isRevertable()){
      out.print("<form action=\"Executor\" method=\"POST\" id=\"revertForm\">\n");
      out.print("<input type=\"hidden\" name=\"token\" id=\"hiddenToken\" />\n");
      out.print("</form>\n");
      out.print("<br><button onclick=\"revert()\" id=\"revertButton\">"+(reverted?"See Reverted Changes":"Revert Changes")+"</button>\n");
      out.print("<script>\n");
      out.print("function revert(){\n");
      out.print("revertButton.disabled = true\n");
      out.print("let req = new XMLHttpRequest()\n");
      out.print("req.open(\"POST\", \"/"+MainGUI.name+"/Reverter\", true)\n");
      out.print("req.setRequestHeader(\"content-type\", \"application/x-www-form-urlencoded\")\n");
      out.print("req.onreadystatechange = function(){\n");
      out.print("if (this.readyState==4){\n");
      out.print("if (this.status==200){\n");
      out.print("hiddenToken.value = this.responseText\n");
      out.print("revertForm.submit()\n");
      out.print("}else{\n");
      out.print("alert(\"Unexpected error: \"+this.statusText)\n");
      out.print("}\n");
      out.print("}\n");
      out.print("}\n");
      out.print("req.send(\"token="+token+"\")\n");
      out.print("}\n");
      out.print("</script>\n");
    }
    if (arr.size()==0){
      if (error==null){
        out.println("<h3>No Problems Detected</h3>");
      }
    }else{
      MessageType t = null;
      String link,path;
      for (Message m:arr){
        if (t!=m.getType()){
          if (t!=null){
            out.println("</table>");
          }
          t = m.getType();
          out.println("<h3>"+t.toString()+"</h3><table>");
        }
        link = m.getLink();
        path = m.getPath();
        if (link==null){
          if (path==null){
            out.println("<tr><td>"+m.getDescription()+"</td><td></td></tr>");
          }else{
            out.println("<tr><td>"+m.getDescription()+"</td><td>"+path+"</td></tr>");
          }
        }else{
          out.println("<tr><td>"+m.getDescription()+"</td><td><a target=\"_blank\" href=\""+link+"\">"+path+"</a></td></tr>");
        }
      }
      out.println("</table>");
    }
    if (error!=null){
      out.println("<h3>Exception Halted Task Execution<h3>");
      out.println("<pre>");
      error.printStackTrace(out);
      out.println("</pre>");
    }
    out.println("</body></html>");
  }
  public void printCSV(PrintWriter out, String linkPrefix, boolean hyperlink){
    if (arr.size()==0){
      if (error==null){
        out.println("No Problems Detected");
      }else{
        error.printStackTrace(out);
      }
    }else{
      MessageType t = null;
      String link = null;
      String path = null;
      for (Message m:arr){
        if (t!=m.getType()){
          t = m.getType();
          out.println();
          out.println();
          out.println(t.toString());
          out.println();
        }
        if (hyperlink){
          link = m.getLink();
        }
        path = m.getPath();
        if (link==null){
          if (path==null){
            out.println('"'+m.getDescription()+'"');
          }else{
            out.println('"'+m.getDescription()+"\",\""+path.replace("\"","\"\"")+'"');
          }
        }else{
          out.println('"'+m.getDescription()+"\",\"=HYPERLINK(\"\""+linkPrefix+link+"\"\",\"\""+path.replace("\"","\"\"")+"\"\")\"");
        }
      }
      if (error!=null){
        out.println();
        error.printStackTrace(out);
      }
    }
  }
  public boolean isRevertable(){
    return completed && modify && reverts.size()>0 && !archived.get();
  }
  public synchronized boolean isReverted(){
    return reverted;
  }
  public synchronized int revert(final SystemConnection con){
    if (modify && completed && !reverted){
      reverted = true;
      final int cap = reverts.size();
      final Container<Integer> pos = new Container<Integer>(0);
      final Iterator<RevertAction> iter = reverts.iterator();
      final Container<Boolean> bool = new Container<Boolean>(false);
      Display d = ProgressHandler.start(true, new Task(){
        @Override public boolean run() throws Exception {
          con.runWriteAction(FieldAccessFactory.newFieldAccess(), "Reverting changes.", new WriteAction(){
            public void execute(WritableSystemAccess sys) throws Exception {
              long time = System.currentTimeMillis()+ProgressHandler.TIMEOUT;
              while (iter.hasNext()){
                ++pos.x;
                iter.next().revert(d);
                if (System.currentTimeMillis()>time){
                  bool.x = false;
                  return;
                }
              }
              bool.x = true;
              return;
            }
          });
          if (bool.x){
            d.percentComplete = 100;
            return true;
          }else{
            d.percentComplete = 100*pos.x/cap;
            return false;
          }
        }
      });
      reverterToken = d.getToken();
      d.username = con.getOperator().getLoginName();
    }
    return reverterToken;
  }
}