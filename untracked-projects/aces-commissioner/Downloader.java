import java.io.*;
import java.nio.file.*;
import javax.servlet.*;
import javax.servlet.http.*;
/**
 * Sends a CSV changelog file to the client.
 */
public class Downloader extends HttpServlet {
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
          res.sendError(404, "Invalid token. Note that tokens expire an hour after task completion.");
        }else if (d.archived.get()){
          res.setContentType("application/octet-stream");
          res.setHeader("Content-Disposition","attachment;filename=\"changelog.csv\"");
          try{
            byte[] arr;
            synchronized (d.fileLock){
              arr = Files.readAllBytes(d.csv);
            }
            ServletOutputStream out = res.getOutputStream();
            out.write(arr);
            out.flush();
          }catch(Exception e){
            PrintWriter out = res.getWriter();
            e.printStackTrace(out);
            out.flush();
          }
        }else if (d.completed){
          PrintWriter out = res.getWriter();
          res.setContentType("application/octet-stream");
          res.setHeader("Content-Disposition","attachment;filename=\"changelog.csv\"");
          d.printCSV(out, req.getScheme()+"://"+req.getServerName()+':'+req.getServerPort(), true);
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