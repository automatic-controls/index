import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Handles AJAX requests to retrieve task summary information.
 */
public class Summary extends HttpServlet {
  private final static SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm a");
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    final StringBuilder sb = new StringBuilder();
    ProgressHandler.forEach(new java.util.function.Consumer<Display>(){
      public void accept(Display d){
        String token = String.valueOf(d.getToken());
        boolean completed = d.completed;
        boolean archived = d.archived.get();
        boolean cancelButtonEnabled = false;
        boolean revertButtonEnabled = false;
        if (!archived){
          d.setStatus();
        }
        String status = d.status;
        if (!archived){
          if (completed){
            if (!d.isReverted() && d.isRevertable()){
              revertButtonEnabled = true;
            }
          }else if (!d.stop){
            cancelButtonEnabled = true;
          }
        }
        sb.append("<tr>");
        sb.append("<td>"+token+"</td>");
        if (archived){
          sb.append("<td>N/A</td>");
        }else{
          sb.append("<td><button onclick=\""+(completed?"result":"exec")+"Link("+token+")\">"+(completed?"Results":"View")+"</button></td>");
        }
        sb.append("<td id=\"status"+token+"\">"+status+"</td>");
        sb.append("<td>"+(d.modify?"True":"False")+"</td>");
        sb.append("<td>"+d.username+"</td>");
        sb.append("<td>"+dateFormat.format(new Date(d.startTime))+"</td>");
        sb.append("<td>"+(cancelButtonEnabled?"<button onclick=\"cancel(this,status"+token+','+token+")\">Cancel</button>":"N/A")+"</td>");
        sb.append("<td>"+(revertButtonEnabled?"<button onclick=\"revert(this,status"+token+','+token+")\">Revert</button>":"N/A")+"</td>");
        if (completed){
          sb.append("<td><a target=\"_blank\" href=\"/"+MainGUI.name+"/Downloader?token="+token+"\" download=\"changelog.csv\">changelog.csv</a></td>");
        }else{
          sb.append("<td>N/A</td>");
        }
        sb.append("</tr>\n");
      }
    });
    PrintWriter out = res.getWriter();
    res.setContentType("text/plain");
    out.print(sb.toString());
    out.flush();
  }
}