import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.access.*;
/**
 * Handles AJAX requests to revert a specified task.
 */
public class Reverter extends HttpServlet {
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    final String token = req.getParameter("token");
    if (token==null){
      res.sendError(400, "Parameter \"token\" was not specified.");
    }else{
      try{
        Display d = ProgressHandler.get(Integer.parseInt(token));
        if (d==null){
          res.sendError(404, "Task does not exist.");
        }else if (d.isRevertable()){
          try{
            String str = String.valueOf(d.revert(DirectAccess.getDirectAccess().getUserSystemConnection(req)));
            PrintWriter out = res.getWriter();
            res.setContentType("text/plain");
            out.print(str);
            out.flush();
          }catch(Exception e){
            res.sendError(400, "Task cannot be reverted.");
          }
        }else{
          res.sendError(400, "Task cannot be reverted.");
        }
      }catch(NumberFormatException e){
        res.sendError(400, "Unable to parse \"token\" parameter.");
      }
    }
  }
}