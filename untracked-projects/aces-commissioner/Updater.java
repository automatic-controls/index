import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Handles AJAX requests to update the progress bar for task completion.
 * Client-side Javascript should handle HTTP codes 200, 400, 401, 404, and 500.
 */
public class Updater extends HttpServlet {
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
        }else if (d.archived.get()){
          res.sendError(404, "Cannot view archived task.");
        }else if (d.error==null){
          int x = d.percentComplete;
          if (x>=100 && !d.completed){
            x = 99;
          }else if (d.completed){
            x = 100;
          }
          PrintWriter out = res.getWriter();
          res.setContentType("text/plain");
          out.print((char)x);
          out.flush();
        }else{
          res.sendError(500, "Error encountered while executing task.");
        }
      }catch(NumberFormatException e){
        res.sendError(400, "Unable to parse \"token\" parameter.");
      }
    }
  }
}
