import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Handles requests to cancel a task.
 */
public class Cancel extends HttpServlet {
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    final String token = req.getParameter("token");
    if (token!=null){
      try{
        Display d = ProgressHandler.get(Integer.parseInt(token));
        if (!d.completed){
          d.stop = true;
        }
      }catch(NumberFormatException e){}
    }
  }
}
