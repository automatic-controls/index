import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.access.*;
/**
 * Handles AJAX requests to load portions of the geographic tree into the GUI.
 * Client-side Javascript should handle HTTP codes 200, 400, and 401.
 */
public class Expander extends HttpServlet {
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    final String path = req.getParameter("path");
    if (path!=null){
      try{
        final StringBuilder sb = new StringBuilder();
        DirectAccess.getDirectAccess().getRootSystemConnection().runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadAction(){
          public void execute(SystemAccess sys) throws Exception {
            Location loc = sys.getTree(SystemTree.Geographic).resolve(path);
            if (loc.getType()!=LocationType.Equipment){
              String s;
              LocationType t;
              for (Location x:loc.getChildren()){
                t = x.getType();
                if (t==LocationType.Area || t==LocationType.Equipment || t==LocationType.Directory){
                  s = x.getPersistentLookupString(true);
                  sb.append((char)s.length());
                  sb.append(s);
                  s = x.getDisplayName();
                  sb.append((char)s.length());
                  sb.append(s);
                }
              }
            }
          }
        });
        PrintWriter out = res.getWriter();
        res.setContentType("text/plain");
        out.println(sb.toString());
        out.flush();
      }catch(Exception e){
        res.sendError(400, "Unable to access geographic tree.");
      }
    }else{
      res.sendError(400, "Parameter \"path\" was not specified.");
    }
  }
}