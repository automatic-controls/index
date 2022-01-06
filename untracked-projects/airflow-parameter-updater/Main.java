import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.*;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.node.*;
/*      NOTE:
  If the uploaded file size is very large, the server may crash with an OutOfMemoryError.
  TODO - Maybe add an upper bound to locs.size() in the parsing loop which immediately returns with an error whenever the upper bound is surpassed.
*/
@MultipartConfig
public class Main extends HttpServlet {
  private final static String addonName = AddOnInfo.getAddOnInfo().getName();
  protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    final PrintWriter out = res.getWriter();
    String path = req.getParameter("path");
    String data = req.getParameter("data");
    if (path!=null){
      //AJAX update to expand a section of the geographic tree on the GUI
      res.setContentType("text/plain");
      try{
        final StringBuilder sb = new StringBuilder();
        DirectAccess.getDirectAccess().getUserSystemConnection(req).runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadAction(){
          public void execute(SystemAccess sys) throws Exception {
            Location loc = sys.getTree(SystemTree.Geographic).resolve(path);
            if (loc.getType()!=LocationType.Equipment){
              String s;
              LocationType t;
              for (Location x:loc.getChildren()){
                t = x.getType();
                if (t==LocationType.Area || t==LocationType.Equipment){
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
        out.println(sb.toString());
      }catch(Exception e){
        out.flush();
      }
    }else if (data!=null){
      final ArrayList<Loc> locs = new ArrayList<Loc>();
      final StringBuilder sb = new StringBuilder();
      int i,j;
      int len = data.length();
      char c;
      for (i=0;i<len;++i){
        //Parses data retrieved from the client
        //Specifies where on the geographic tree to search for airflow microblocks
        c = data.charAt(i);
        if (c==',' && sb.length()>0){
          locs.add(new Loc(sb.toString()));
          sb.setLength(0);
        }else{
          sb.append(c);
        }
      }
      data = null;
      if (req.getParameter("generate")==null){
        res.setContentType("text/plain");
        if (locs.size()>0){
          final ArrayList<Airflow> arr = new ArrayList<Airflow>();
          try(
            //Parses the file uploaded by the client
            //Specifies airflow microblock parameters
            BufferedReader in = new BufferedReader(new InputStreamReader(req.getPart("file").getInputStream()));
          ){
            boolean go = true;
            boolean ready;
            boolean end;
            Airflow air;
            while (go){
              //Loop over each line
              air = new Airflow();
              end = false;
              while (go){
                //Loop over tokens in a given line
                i = in.read();
                if (i==-1){
                  go = false;
                  break;
                }
                c = (char)i;
                if (c=='\n' || c=='\r'){
                  break;
                }else if (c==','){
                  while (go){
                    i = in.read();
                    if (i==-1){
                      go = false;
                      break;
                    }
                    c = (char)i;
                    if (c=='\n'){
                      break;
                    }
                  }
                  break;
                }
                sb.setLength(0);
                if (c=='"'){
                  ready = false;
                  while (go){
                    //Loop over characters in a given token
                    i = in.read();
                    if (i==-1){
                      go = false;
                      break;
                    }
                    c = (char)i;
                    if (c=='\n' || c=='\r'){
                      end = true;
                      break;
                    }else if (ready){
                      if (c==','){
                        break;
                      }else{
                        ready = false;
                        sb.append(c);
                      }
                    }else if (c=='"'){
                      ready = true;
                    }else{
                      sb.append(c);
                    }
                  }
                }else{
                  sb.append(c);
                  while (go){
                    //Loop over characters in a given token
                    i = in.read();
                    if (i==-1){
                      go = false;
                      break;
                    }
                    c = (char)i;
                    if (c=='\n' || c=='\r'){
                      end = true;
                      break;
                    }else if (c==','){
                      break;
                    }else{
                      sb.append(c);
                    }
                  }
                }
                if (sb.length()>0){
                  air.next(sb.toString());
                }
                if (!go || end){
                  break;
                }
              }
              if (air.valid){
                arr.add(air);
              }
            }
          }catch(Exception e){
            arr.clear();
            e.printStackTrace(out);
          }
          if (arr.size()>0){
            try{
              sb.setLength(0);
              final SystemConnection syscon = DirectAccess.getDirectAccess().getUserSystemConnection(req);
              final ArrayList<Change> changes = new ArrayList<Change>();
              boolean duplicates = syscon.runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadActionResult<Boolean>(){
                public Boolean execute(SystemAccess sys) throws Exception {
                  Tree geo = sys.getTree(SystemTree.Geographic);
                  boolean b = false;
                  for (Loc loc:locs){
                    try{
                      loc.loc = geo.resolve(loc.str);
                    }catch(Exception e){
                      b = true;
                    }
                  }
                  if (b){
                    sb.append("Warning - Unable to resolve location(s) on the geographic tree.\n");
                  }
                  b = false;
                  for (Loc loc:locs){
                    b|=iterate(loc.loc, arr, changes, sb);
                  }
                  return b;
                }
              });
              if (duplicates){
                sb.append("\nError - Tag(s) matched to more than one equipment.\n");
                sb.append("Please ensure the geographic tree selections are sufficiently restrictive.\n");
                len = changes.size();
                Change a,b;
                for (i=0;i<len;++i){
                  a = changes.get(i);
                  duplicates = false;
                  if (a!=null){
                    for (j=i+1;j<len;++j){
                      b = changes.get(j);
                      if (b!=null && a.air==b.air){
                        if (!duplicates){
                          duplicates = true;
                          sb.append('\n'+a.air.tag+'\n');
                          sb.append("    "+a.path+'\n');
                          changes.set(i,null);
                        }
                        sb.append("    "+b.path+'\n');
                        changes.set(j,null);
                      }
                    }
                  }
                }
              }else{
                if (changes.size()>0){
                  syscon.runWriteAction(FieldAccessFactory.newFieldAccess(), "Updating airflow microblock parameters.", new WriteAction(){
                    public void execute(WritableSystemAccess sys) throws Exception {
                      for (Change chg:changes){
                        if (chg.air.manSpec!=Airflow.UNKNOWN){
                          chg.manSpec.setValue(chg.air.manSpec);
                        }
                        if (chg.air.coolMax!=Airflow.UNKNOWN){
                          chg.coolMax.setValue(chg.air.coolMax);
                        }
                        if (chg.air.occMin!=Airflow.UNKNOWN){
                          chg.occMin.setValue(chg.air.occMin);
                        }
                        if (chg.air.heatMax!=Airflow.UNKNOWN){
                          chg.heatMax.setValue(chg.air.heatMax);
                        }
                        if (chg.air.auxHeatMin!=Airflow.UNKNOWN){
                          chg.auxHeatMin.setValue(chg.air.auxHeatMin);
                        }
                        if (chg.air.unoccMin!=Airflow.UNKNOWN){
                          chg.unoccMin.setValue(chg.air.unoccMin);
                        }
                      }
                    }
                  });
                }
                for (Change chg:changes){
                  sb.append(chg.toString());
                }
              }
              sb.append('\n');
              for (Airflow air:arr){
                if (!air.matched){
                  sb.append("No matches found for tag: "+air.tag+'\n');
                }
              }
              out.println(sb.toString());
            }catch(Exception e){
              e.printStackTrace(out);
            }
          }else{
            out.println("The uploaded CSV document does not contain any usable information.");
          }
        }else{
          out.println("You have not made any search selections on the geographic tree.");
        }
      }else{
        try{
          res.setContentType("application/octet-stream");
          res.setHeader("Content-Disposition","attachment;filename=\"AirflowParams.csv\"");
          sb.setLength(0);
          sb.append("Tag Name,Manufacturer Specified Airflow,Cooling Max Airflow,Occupied Min Airflow,Heating Max Airflow,Auxiliary Heat Min Airflow,Unoccupied Min Airflow\n");
          final SystemConnection syscon = DirectAccess.getDirectAccess().getUserSystemConnection(req);
            syscon.runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadAction(){
              public void execute(SystemAccess sys) throws Exception {
                Tree geo = sys.getTree(SystemTree.Geographic);
                boolean b = false;
                for (Loc loc:locs){
                  try{
                    loc.loc = geo.resolve(loc.str);
                  }catch(Exception e){
                    b = true;
                  }
                }
                for (Loc loc:locs){
                  iterate(loc.loc, sb);
                }
                if (b){
                  sb.append("Warning,Unable to resolve location(s) on the geographic tree\n");
                }
              }
            });
          out.print(sb.toString());
        }catch(Exception e){
          e.printStackTrace(out);
        }
      }
    }else{
      res.setContentType("text/html");
      try{
        String[] arr = DirectAccess.getDirectAccess().getUserSystemConnection(req).runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadActionResult<String[]>(){
          public String[] execute(SystemAccess sys) throws Exception {
            Location geo = sys.getGeoRoot();
            return new String[]{geo.getDisplayName(), geo.getPersistentLookupString(true)};
          }
        });
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<title>\n");
        sb.append("Airflow Parameter Updater\n");
        sb.append("</title>\n");
        sb.append("<style> td{padding:5px;border:solid 2px black;} table{border:solid 2px black;border-collapse:collapse;}</style>\n");
        sb.append("<script>\n");
        sb.append("function toggleHelp(){\n");
        sb.append("helpDisplayed^=true\n");
        sb.append("if (helpDisplayed){\n");
        sb.append("fileForm.appendChild(helpTextRef)\n");
        sb.append("fileForm.style.marginBottom = \"0em\"\n");
        sb.append("}else{\n");
        sb.append("if (!helpTextRef){\n");
        sb.append("helpTextRef = helpText\n");
        sb.append("}\n");
        sb.append("fileForm.removeChild(helpTextRef)\n");
        sb.append("fileForm.style.marginBottom = \"1em\"\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("function setupNode(n){\n");
        sb.append("let m = document.createElement(\"div\")\n");
        sb.append("m.style.marginLeft = \"1em\"\n");
        sb.append("n.parentNode.insertBefore(m, n.nextSibling)\n");
        sb.append("let checkbox = document.createElement(\"input\")\n");
        sb.append("checkbox.type = \"checkbox\"\n");
        sb.append("n.parentNode.insertBefore(checkbox,n)\n");
        sb.append("n.onmouseover = function(){\n");
        sb.append("if (mouseDown){\n");
        sb.append("checkbox.checked^=true\n");
        sb.append("checkbox.onchange()\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("checkbox.onchange = function(){\n");
        sb.append("var x\n");
        sb.append("if (checkbox.checked){\n");
        sb.append("let arr = m.getElementsByTagName(\"input\")\n");
        sb.append("for (var i=0;i<arr.length;++i){\n");
        sb.append("x = arr[i]\n");
        sb.append("if (!x.checked){\n");
        sb.append("x.checked = true\n");
        sb.append("x.onchange()\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}else if (!n.root){\n");
        sb.append("x = n.parentNode.previousSibling.previousSibling\n");
        sb.append("if (x.checked){\n");
        sb.append("x.checked = false\n");
        sb.append("x.onchange()\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("checkbox.checked = !n.root && n.parentNode.previousSibling.previousSibling.checked\n");
        sb.append("n.expanded = false\n");
        sb.append("n.onclick = function(){\n");
        sb.append("n.expanded^=true\n");
        sb.append("if (n.expanded){\n");
        sb.append("let req = new XMLHttpRequest()\n");
        sb.append("req.open(\"POST\",\""+addonName+"\",true)\n");
        sb.append("req.setRequestHeader(\"content-type\", \"application/x-www-form-urlencoded\")\n");
        sb.append("req.onreadystatechange = function(){\n");
        sb.append("if (this.readyState==4 && this.status==200){\n");
        sb.append("let res = this.responseText\n");
        sb.append("var i,j,k,l,x\n");
        sb.append("k = false\n");
        sb.append("for (var i=0;i<res.length;){\n");
        sb.append("j = res.charCodeAt(i++)\n");
        sb.append("l = res.substr(i,j)\n");
        sb.append("i+=j\n");
        sb.append("k^=true\n");
        sb.append("if (k){\n");
        sb.append("x = document.createElement(\"button\")\n");
        sb.append("x.path = l\n");
        sb.append("}else{\n");
        sb.append("x.innerHTML = l\n");
        sb.append("m.appendChild(x)\n");
        sb.append("setupNode(x)\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("req.send(\"path=\"+n.path)\n");
        sb.append("}else{\n");
        sb.append("while (m.firstChild){\n");
        sb.append("m.removeChild(m.lastChild)\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("function validateSubmit(){\n");
        sb.append("if (fileField.value.length==0){\n");
        sb.append("alert(\"Please choose a file!\")\n");
        sb.append("return false\n");
        sb.append("}\n");
        sb.append("data.value = getData(rootDiv)\n");
        sb.append("if (data.value.length==0){\n");
        sb.append("alert(\"Please make a selection on the geographic tree!\")\n");
        sb.append("return false\n");
        sb.append("}\n");
        sb.append("return true\n");
        sb.append("}\n");
        sb.append("function getData(div){\n");
        sb.append("let str = \"\"\n");
        sb.append("let arr = div.children\n");
        sb.append("var x\n");
        sb.append("for (var i=0;i<arr.length;++i){\n");
        sb.append("x = arr[i]\n");
        sb.append("if (x.tagName===\"BUTTON\"){\n");
        sb.append("if (x.previousSibling.checked){\n");
        sb.append("str = str.concat(x.path, ',')\n");
        sb.append("}else{\n");
        sb.append("str = str.concat(getData(x.nextSibling))\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("return str\n");
        sb.append("}\n");
        sb.append("function deselect(){\n");
        sb.append("let arr = rootDiv.getElementsByTagName(\"INPUT\")\n");
        sb.append("for (var i=0;i<arr.length;++i){\n");
        sb.append("arr[i].checked = false\n");
        sb.append("}\n");
        sb.append("}\n");
        sb.append("function changeHREF(x){\n");
        sb.append("x.href = \"/"+addonName+"?generate&data=\"+encodeURIComponent(getData(rootDiv))\n");
        sb.append("}\n");
        sb.append("</script>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<h1 style=\"text-align:center\">Airflow Parameter Updater</h1>\n");
        sb.append("<form id=\"fileForm\" action=\""+addonName+"\" method=\"POST\" enctype=\"multipart/form-data\" target=\"_blank\" onsubmit=\"return validateSubmit()\">\n");
        sb.append("<button type=\"button\" onclick=\"toggleHelp()\" style=\"margin-left:1em\">Toggle Help</button>\n");
        sb.append("<button type=\"button\" onclick=\"deselect()\" style=\"margin-left:1em\">Deselect All</button>\n");
        sb.append("<a style=\"margin-left:1em\" target=\"_blank\" download=\"AirflowParams.csv\" href=\"#\" onclick=\"changeHREF(this)\">Export</a>\n");
        sb.append("<input type=\"submit\" value=\"Import\" style=\"margin-left:1em\">\n");
        sb.append("<input type=\"file\" id=\"fileField\" name=\"file\" style=\"margin-left:1em;width:40em\">\n");
        sb.append("<input type=\"hidden\" name=\"data\" id=\"data\">\n");
        sb.append("<div id=\"helpText\" style=\"margin-left:1em;margin-top:1em\">\n");
        sb.append("Import a CSV file which specifies tag names and the corresponding airflow microblock parameters.<br>\n");
        sb.append("The program will search the geographic tree to find a control program whose display name equals the tag name (case insensitive).<br>\n");
        sb.append("If the matched control program contains an airflow microblock, the parameters will be set as specified in the CSV document.<br>\n");
        sb.append("Make selections on the geographic tree to restrict the search space. Hold and drag to select or deselect multiple entries at once.<br>\n");
        sb.append("If any tag is matched to more than one control program, no changes will be made, and an error will be thrown.<br>\n");
        sb.append("<br>The template for a row in the CSV file is:<br>\n");
        sb.append("<table><tr><td>Tag Name</td><td>Manufacturer Specified Airflow</td><td>Cooling Max Airflow</td><td>Occupied Min Airflow</td><td>Heating Max Airflow</td><td>Auxiliary Heat Min Airflow</td><td>Unoccupied Min Airflow</td></tr></table>\n");
        sb.append("<br>When opened with a text-editor, an example CSV file might look like:<br>\n");
        sb.append("<i>\n");
        sb.append("VAV-1.1,1500,900,350,400,350,-1<br>\n");
        sb.append("VAV-1.2,2050,1300,-1,700,500,0<br>\n");
        sb.append("</i><br>\n");
        sb.append("All 7 columns need to be present, but cells with negative numbers will be ignored (i.e. that parameter will not be changed).<br>\n");
        sb.append("Alternatively, you can export a CSV document that already contains the airflow parameters in the proper format.\n");
        sb.append("</p>\n");
        sb.append("</form>\n");
        sb.append("<script>\n");
        sb.append("var helpDisplayed = true\n");
        sb.append("var helpTextRef\n");
        sb.append("toggleHelp()\n");
        sb.append("var mouseDown = false\n");
        sb.append("document.onmousedown = function(){\n");
        sb.append("mouseDown = true\n");
        sb.append("}\n");
        sb.append("document.onmouseup = function(){\n");
        sb.append("mouseDown = false\n");
        sb.append("}\n");
        sb.append("var rootDiv = document.createElement(\"div\")\n");
        sb.append("rootDiv.style.marginLeft = \"1em\"\n");
        sb.append("rootDiv.style.userSelect = \"none\"\n");
        sb.append("rootDiv.style.webkitUserSelect = \"none\"\n");
        sb.append("let root = document.createElement(\"button\")\n");
        sb.append("root.innerHTML = \""+arr[0]+"\"\n");
        sb.append("root.path = \""+arr[1]+"\"\n");
        sb.append("rootDiv.appendChild(root)\n");
        sb.append("root.root = true\n");
        sb.append("setupNode(root)\n");
        sb.append("document.body.appendChild(rootDiv)\n");
        sb.append("</script>\n");
        sb.append("</body>\n");
        sb.append("</htm\n");
        out.println(sb.toString());
      }catch(Exception e){
        out.println("<!DOCTYPE html>");
        out.println("<html><head>");
        out.println("<title>Airflow Parameter Updater</title>");
        out.println("</head><body>");
        out.println("<h1 style=\"text-align:center\">Error Occurred</h1>");
        out.println("</body></html>");
        e.printStackTrace(out);
      }
    }
  }
  public void iterate(Location loc, StringBuilder sb) throws Exception {
    LocationType t = loc.getType();
    if (t==LocationType.Area || t==LocationType.System){
      for (Location l:loc.getChildren()){
        iterate(l,sb);
      }
    }else if (t==LocationType.Equipment){
      String name = loc.getDisplayName();
      String manSpec=null,coolMax=null,occMin=null,heatMax=null,auxHeatMin=null,unoccMin=null;
      for (Location l:loc.getChildren()){
        if (l.toNode().eval(".node-type").equals("270")){
          String refName;
          for (Location a:l.getChildren()){
            if (a.getReferenceName().equals("flow_tab")){
              for (Node n:a.toNode().getChildren()){
                refName = n.getReferenceName();
                if (refName.equals("flow_at_one_inch")){
                  manSpec = n.getValue();
                }else if (refName.equals("max_cool")){
                  coolMax = n.getValue();
                }else if (refName.equals("occ_min")){
                  occMin = n.getValue();
                }else if (refName.equals("max_heat")){
                  heatMax = n.getValue();
                }else if (refName.equals("aux_min")){
                  auxHeatMin = n.getValue();
                }else if (refName.equals("unocc_min")){
                  unoccMin = n.getValue();
                }
              }
              break;
            }
          }
          break;
        }
      }
      if (manSpec!=null && coolMax!=null && occMin!=null && heatMax!=null && auxHeatMin!=null && unoccMin!=null){
        name = '"'+name.replace("\"","\"\"")+'"';
        sb.append(name).append(',').append(manSpec).append(',').append(coolMax).append(',').append(occMin).append(',').append(heatMax).append(',').append(auxHeatMin).append(',').append(unoccMin).append('\n');
      }
    }
  }
  public boolean iterate(Location loc, ArrayList<Airflow> arr, ArrayList<Change> changes, StringBuilder sb) throws Exception {
    boolean ret = false;
    LocationType t = loc.getType();
    if (t==LocationType.Equipment){
      String name = loc.getDisplayName().toLowerCase();
      for (Airflow air:arr){
        if (name.equals(air.tag)){
          boolean found = false;
          for (Location l:loc.getChildren()){
            if (l.toNode().eval(".node-type").equals("270")){
              String refName;
              for (Location a:l.getChildren()){
                if (a.getReferenceName().equals("flow_tab")){
                  Change chg = new Change();
                  chg.air = air;
                  chg.path = loc.getRelativeDisplayPath(null);
                  for (Node n:a.toNode().getChildren()){
                    refName = n.getReferenceName();
                    if (refName.equals("flow_at_one_inch")){
                      chg.manSpec = n;
                    }else if (refName.equals("max_cool")){
                      chg.coolMax = n;
                    }else if (refName.equals("occ_min")){
                      chg.occMin = n;
                    }else if (refName.equals("max_heat")){
                      chg.heatMax = n;
                    }else if (refName.equals("aux_min")){
                      chg.auxHeatMin = n;
                    }else if (refName.equals("unocc_min")){
                      chg.unoccMin = n;
                    }
                  }
                  if (chg.isValid()){
                    found = true;
                    changes.add(chg);
                    if (air.matched){
                      ret = true;
                    }else{
                      air.matched = true;
                    }
                  }
                  break;
                }
              }
              break;
            }
          }
          if (!found){
            sb.append("Warning - Matched equipment \""+loc.getRelativeDisplayPath(null)+"\" does not contain an airflow microblock.\n");
          }
          break;
        }
      }
    }else if (t==LocationType.Area || t==LocationType.System){
      for (Location l:loc.getChildren()){
        ret|=iterate(l,arr,changes,sb);
      }
    }
    return ret;
  }
}
class Airflow {
  public final static String UNKNOWN = "NoChange";
  public String tag = null;
  public String manSpec = null;
  public String coolMax = null;
  public String occMin = null;
  public String heatMax = null;
  public String auxHeatMin = null;
  public String unoccMin = null;
  public boolean valid = false;
  public boolean matched = false;
  @Override
  public boolean equals(Object obj){
    return this==obj;
  }
  public void next(String token){
    if (tag==null){
      tag = token.toLowerCase();
    }else if (isNumeric(token)){
      try{
        if (Integer.parseInt(token)<0){
          token = UNKNOWN;
        }
        if (manSpec==null){
          manSpec = token;
        }else if (coolMax==null){
          coolMax = token;
        }else if (occMin==null){
          occMin = token;
        }else if (heatMax==null){
          heatMax = token;
        }else if (auxHeatMin==null){
          auxHeatMin = token;
        }else if (unoccMin==null){
          unoccMin = token;
          valid = true;
        }
      }catch(Exception e){}
    }
  }
  /**
   * Exceptions are generally expensive, so we use this method before attempting to invoke {@code Integer.parseInt(String)}.
   */
  public static boolean isNumeric(String str){
    int len = str.length();
    if (len==0){
      return false;
    }
    for (int i=(str.charAt(0)=='-'?1:0);i<len;++i){
      char c = str.charAt(i);
      if (c<'0' || c>'9'){
        return false;
      }
    }
    return true;
  }
}
class Change {
  public Airflow air = null;
  public Node manSpec = null;
  public Node coolMax = null;
  public Node occMin = null;
  public Node heatMax = null;
  public Node auxHeatMin = null;
  public Node unoccMin = null;
  public String path = null;
  public boolean isValid(){
    return air!=null && manSpec!=null && coolMax!=null && occMin!=null && heatMax!=null && auxHeatMin!=null && unoccMin!=null && path!=null;
  }
  @Override
  public String toString(){
    return '\n'+path+"\nCooling Max Airflow: "+f(air.coolMax)+"\nHeating Max Airflow: "+f(air.heatMax)+"\nOccupied Min Airflow: "+f(air.occMin)+"\nUnoccupied Min Airflow: "+f(air.unoccMin)+"\nAuxiliary Heat Min Airflow: "+f(air.auxHeatMin)+"\nManufacturer's specified air flow at 1\" water column: "+f(air.manSpec)+'\n';
  }
  private static String f(String str){
    return str==Airflow.UNKNOWN?str:str+" cfm";
  }
}
class Loc {
  String str;
  Location loc;
  public Loc(String str){
    this.str = str;
    loc = null;
  }
}