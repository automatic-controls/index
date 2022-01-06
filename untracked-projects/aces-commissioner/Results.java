import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Handles requests to view the results of a task's completion.
 */
public class Results extends HttpServlet {
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
        }else if (d.completed){
          PrintWriter out = res.getWriter();
          res.setContentType("text/html");
          d.printHTML(out, token);
          out.flush();
        }else{
          res.sendError(404, "Operation has not completed.");
        }
      }catch(NumberFormatException e){
        res.sendError(400, "Unable to parse \"token\" parameter.");
      }
    }
  }
}