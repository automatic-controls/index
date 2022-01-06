import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.web.*;
import com.controlj.green.addonsupport.access.*;
/**
 * Handles AJAX requests to list all control programs under specified locations.
 */
public class Checker extends HttpServlet {
  /**
   * Parses data retrieved from the client.
   * Specifies which parts of the geographic tree should be processed.
   */
  public static void parseLocations(ArrayList<String> arr, String data){
    StringBuilder sb = new StringBuilder();
    int len = data.length();
    char c;
    for (int i=0;i<len;++i){
      c = data.charAt(i);
      if (c==','){
        arr.add(sb.toString());
        sb.setLength(0);
      }else{
        sb.append(c);
      }
    }
  }
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    final ArrayList<String> locations = new ArrayList<String>();
    {
      String data = req.getParameter("data");
      if (data==null){
        res.sendError(400, "Parameter \"data\" was not specified.");
        return;
      }else{
        parseLocations(locations,data);
      }
      if (locations.size()==0){
        res.sendError(400, "Please select at least one location on the geographic tree.");
        return;
      }
    }
    try{
      final StringBuilder sb = new StringBuilder(256);
      sb.append("<ul>\n");
      DirectAccess.getDirectAccess().getRootSystemConnection().runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadAction(){
        public void execute(SystemAccess sys) throws Exception {
          Tree tree = sys.getTree(SystemTree.Geographic);
          Location root = sys.getGeoRoot();
          for (String str:locations){
            recurse(sb,tree.resolve(str),root,req);
          }
        }
      });
      sb.append("</ul>");
      PrintWriter out = res.getWriter();
      res.setContentType("text/plain");
      out.println(sb.toString());
      out.flush();
    }catch(Exception e){
      res.sendError(400, "Unable to access geographic tree.");
    }
  }
  private static void recurse(StringBuilder sb, Location loc, Location root, HttpServletRequest req){
    LocationType t = loc.getType();
    if (t==LocationType.Equipment){
      try{
        sb.append("<li><a target=\"_blank\" href=\""+Link.createLink(UITree.GEO, loc).getURL(req)+"\">"+loc.getRelativeDisplayPath(root)+"</a></li>\n");
      }catch(Exception e){
        sb.append("<li>"+loc.getRelativeDisplayPath(root)+"</li>\n");
      }
    }else{
      for (Location l:loc.getChildren()){
        t = l.getType();
        if (t==LocationType.Equipment || t==LocationType.Area || t==LocationType.System || t==LocationType.Directory){
          recurse(sb,l,root,req);
        }
      }
    }
  }
}