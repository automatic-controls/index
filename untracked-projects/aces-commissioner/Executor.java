import java.io.*;
import java.util.*;
import java.util.function.*;
import javax.servlet.*;
import javax.servlet.http.*;
import com.controlj.green.addonsupport.web.*;
import com.controlj.green.addonsupport.access.*;
import com.controlj.green.addonsupport.access.node.*;
import com.controlj.green.addonsupport.access.aspect.*;
import com.controlj.green.addonsupport.access.schedule.*;
import com.controlj.green.addonsupport.access.schedule.template.*;
import com.controlj.green.addonsupport.bacnet.data.datetime.*;
/*
Mircoblock Node Types
  BAI=211
  BAO=212
  BAV=213
  BBI=214
  BBO=215
  BBV=216
  BMSV=219
  BTRN=230
  BALM=268
  BRS=269 (RS Sensor)
  BAF=270
  BSVI=286
  ASVI=287
  Sensor Binder=288
  BSPTCS=289 (Setpoint)
  ANI,ANO2,ANI2,BNI,BNO2=506

Unit List
  other=-1
  mA=2
  A=3
  V=5
  kVA=9
  kVAR=12
  kWh=19
  therm=21
  Btu/lb dry air=24
  Hz=27
  %RH=29
  ft-candle=38
  kW=48
  psi=56
  in H2O=58
  Celcius=62
  Farenheight=64
  hr=71
  min=72
  CFM=84
  GPM(UK)=86
  GPM=89
  Celcius/hr=91
  Celcius/min=92
  Farenheight/hr=93
  Farenheight/min=94
  no units=95
  PPM=96
  %=98
  Btu/lb=117
  kBtu/hr=157
  gal/hr=4098
  psig=4102
  MBtu/hr=4104
  %Open=4109
*/
/**
 * Handles requests to begin asynchronous execution of commissioning actions.
 */
public class Executor extends HttpServlet {
  @Override protected void doGet(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    doPost(req, res);
  }
  @Override protected void doPost(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
    try{
      String token = req.getParameter("token");
      Display dis;
      if (token==null){
        final boolean modify = req.getParameter("modify")!=null;
        final boolean visibleAlarm = req.getParameter("visibleAlarm")!=null;
        final boolean ackAlarm = req.getParameter("ackAlarm")!=null;
        final boolean balmPotAlarm = req.getParameter("balmPotAlarm")!=null;
        final boolean catAlarm = req.getParameter("catAlarm")!=null && !modify;
        final boolean errorNet = req.getParameter("errorNet")!=null && !modify;
        final boolean setupSchedules = req.getParameter("setupSchedules")!=null && !modify;
        final boolean visibleIO = req.getParameter("visibleIO")!=null;
        final boolean checkoutIO = req.getParameter("checkoutIO")!=null && !modify;
        final boolean duplicateIO = req.getParameter("duplicateIO")!=null && !modify;
        final boolean lockedIO = req.getParameter("lockedIO")!=null;
        final boolean errorIO = req.getParameter("errorIO")!=null && !modify;
        final boolean errorTrend = req.getParameter("errorTrend")!=null && !modify;
        final boolean setupTrend = req.getParameter("setupTrend")!=null;
        final boolean trendBTRN = req.getParameter("trendBTRN")!=null;
        boolean any = false;
        any|=visibleAlarm;
        any|=ackAlarm;
        any|=balmPotAlarm;
        any|=catAlarm;
        any|=errorNet;
        any|=setupSchedules;
        any|=visibleIO;
        any|=checkoutIO;
        any|=duplicateIO;
        any|=lockedIO;
        any|=errorIO;
        any|=errorTrend;
        any|=setupTrend;
        any|=trendBTRN;
        if (!any){
          res.sendError(400, "Please specify which properties you want to commission.");
          return;
        }
        final boolean net = errorNet;
        final boolean io = checkoutIO||duplicateIO||errorIO||lockedIO||visibleIO;
        final boolean alarm = ackAlarm||balmPotAlarm||visibleAlarm||catAlarm;
        final boolean trend = errorTrend||setupTrend||trendBTRN;
        final ArrayList<String> locations = new ArrayList<String>();
        {
          //Parses data retrieved from the client
          //Specifies which parts of the geographic tree should be processed
          String data = req.getParameter("data");
          if (data==null){
            res.sendError(400, "Parameter \"data\" was not specified.");
            return;
          }else{
            Checker.parseLocations(locations,data);
          }
          if (locations.size()==0){
            res.sendError(400, "Please select at least one location on the geographic tree.");
            return;
          }
        }
        final Vars v = new Vars();
        v.req = req;
        final ArrayList<Location> locs = new ArrayList<Location>(locations.size());
        final ArrayList<Location> equipment = new ArrayList<Location>();
        final ArrayList<Action> readActions = new ArrayList<Action>();
        final ArrayList<Action> writeActions = new ArrayList<Action>();
        final SystemConnection con = DirectAccess.getDirectAccess().getUserSystemConnection(req);
        dis = ProgressHandler.start(modify, new Task(){
          @Override public boolean run() throws Exception {
            if (v.phase==1){// 0% -> 1%
              //Resolve identifier strings to valid locations on the geographic tree.
              con.runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadAction(){
                public void execute(SystemAccess sys) throws Exception {
                  v.root = sys.getGeoRoot();
                  Tree tree = sys.getTree(SystemTree.Geographic);
                  for (String str:locations){
                    try{
                      locs.add(tree.resolve(str));
                    }catch(Exception e){
                      d.add(MessageType.ERROR, "Cannot resolve location "+str+" due to "+e.getClass().getSimpleName()+'.', null, null);
                    }
                  }
                }
              });
              if (locs.size()==0){
                d.percentComplete = 100;
                return true;
              }else{
                v.cap = locs.size();
                v.pos = 0;
                v.locIter = locs.iterator();
                d.percentComplete = 1;
                ++v.phase;
                return false;
              }
            }else if (v.phase==2){// 1% -> 5%
              //Form a list of all affected control programs, and look for bad schedules
              boolean ret = con.runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadActionResult<Boolean>(){
                public Boolean execute(SystemAccess sys) throws Exception {
                  long time = System.currentTimeMillis()+ProgressHandler.TIMEOUT;
                  Collection<ScheduleCategory<Boolean>> boolCat = setupSchedules?sys.getScheduleManager().getBooleanScheduleCategories():null;
                  while (v.locIter.hasNext()){
                    recurse(v,d,v.locIter.next(),equipment,setupSchedules,boolCat);
                    ++v.pos;
                    if (System.currentTimeMillis()>time){
                      return false;
                    }
                  }
                  return true;
                }
              });
              if (ret){
                if (equipment.size()==0){
                  d.percentComplete = 100;
                  return true;
                }else{
                  ++v.phase;
                  v.locIter = equipment.iterator();
                  v.cap = equipment.size();
                  v.pos = 0;
                  d.percentComplete = 5;
                  return false;
                }
              }else{
                d.percentComplete = 1+4*v.pos/v.cap;
                return false;
              }
            }else if (v.phase==3){// 5% -> 20%
              //Form a list of readActions
              boolean ret = con.runReadAction(FieldAccessFactory.newDisabledFieldAccess(), new ReadActionResult<Boolean>(){
                public Boolean execute(SystemAccess sys) throws Exception {
                  long time = System.currentTimeMillis()+ProgressHandler.TIMEOUT;
                  while (v.locIter.hasNext()){
                    {
                      //Main block which controls commissioning standards
                      final Location loc = v.locIter.next();
                      final Node mainNode = loc.toNode();
                      final String path = loc.getRelativeDisplayPath(v.root);
                      final LinkHandler linker = new LinkHandler(loc,req);
                      final Collection<Location> children = loc.getChildren();
                      if (loc.hasAspect(AttachedEquipment.class)){
                        final AttachedEquipment eq = loc.getAspect(AttachedEquipment.class);
                        readActions.add(new Action(){
                          public boolean act() throws Exception {
                            try{
                              eq.getColor();
                              return true;
                            }catch(Exception e){
                              if (!modify){
                                d.add(MessageType.COM, "No Communication", path, Link.createLink(UITree.GEO, loc, "properties", "default", "equipment", "view").getURL(req));
                              }
                              return false;
                            }
                          }
                        });
                      }
                      Node node;
                      String nodeType;
                      String refName;
                      if (errorIO || lockedIO || checkoutIO || visibleIO){
                        final Container<Boolean> err = new Container<Boolean>(false);
                        for (Location c:children){
                          node = c.toNode();
                          try{
                            nodeType = node.eval(".node-type");
                            if (nodeType!=null && nodeType.equals("288")){
                              //Add a readAction which checks for sensor binder errors
                              for (Node n:node.getChildren()){
                                node = n;
                                if (node.getReferenceName().equals("asensor_stat")){
                                  final Node nn = node;
                                  final Location cc = c;
                                  readActions.add(new Action(){
                                    public boolean act() throws Exception {
                                      for (Node m:nn.getChildren()){
                                        if (m.getReferenceName().startsWith("index")){
                                          try{
                                            if (!m.getValue().equals("0")){
                                              if (!modify){
                                                d.add(MessageType.IO, "Sensor binder has error", cc.getRelativeDisplayPath(v.root), Link.createLink(UITree.GEO, cc).getURL(req));
                                              }
                                              err.x = true;
                                              break;
                                            }
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), cc.getRelativeDisplayPath(v.root), Link.createLink(UITree.GEO, cc).getURL(req));
                                            err.x = true;
                                            break;
                                          }
                                        }
                                      }
                                      return true;
                                    }
                                  });
                                  break;
                                }
                              }
                              break;
                            }
                          }catch(Exception e){
                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+node.getRelativeReferencePath(mainNode), Link.createLink(UITree.GEO, c).getURL(req));
                            err.x = true;
                            break;
                          }
                        }
                        for (Location c:children){
                          node = c.toNode();
                          try{
                            nodeType = node.eval(".node-type");
                            if (nodeType!=null && (nodeType.equals("286") || nodeType.equals("287"))){
                              /**
                               * Check for ASVI and BSVI errors
                               * Check for locked values
                               * Ensure checkout procedure
                               * Enfore network visibility
                               */
                              final String typeString = nodeType.equals("286")?"BSVI":"ASVI";
                              final Location cc = c;
                              final Container<Node> statusNode = new Container<Node>();
                              for (Node n:node.getChildren()){
                                node = n;
                                refName = node.getReferenceName();
                                if (lockedIO && refName.equals("locked")){
                                  final Node nn = node;
                                  readActions.add(new Action(){
                                    public boolean act() throws Exception {
                                      if (!err.x){
                                        try{
                                          if (nn.getValue().equals("true")){
                                            if (modify){
                                              writeActions.add(new Action(){
                                                public boolean act() throws Exception {
                                                  try{
                                                    nn.setValue("false");
                                                    Message m = new Message(MessageType.IO, typeString+" unlocked", cc.getRelativeDisplayPath(v.root), linker.getIO());
                                                    d.reverts.add(new RevertAction(nn,"true",m));
                                                    d.add(m);
                                                  }catch(Exception e){
                                                    d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), linker.getIO());
                                                  }
                                                  return true;
                                                }
                                              });
                                            }else{
                                              d.add(MessageType.IO, typeString+" is locked", cc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), linker.getIO());
                                        }
                                      }
                                      return true;
                                    }
                                  });
                                }else if (!modify && errorIO && refName.equals("reliability")){
                                  final Node nn = node;
                                  readActions.add(new Action(){
                                    public boolean act() throws Exception {
                                      if (!err.x){
                                        try{
                                          if (!nn.getValue().equals("0")){
                                            d.add(MessageType.IO, typeString+" has fault", cc.getRelativeDisplayPath(v.root), linker.getIO());
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), linker.getIO());
                                        }
                                      }
                                      return true;
                                    }
                                  });
                                }else if (!modify && errorIO && refName.equals("analog_sensor_st_array")){
                                  statusNode.x = node;
                                }else if (!modify && errorIO && refName.equals("sens_enab_flags")){
                                  final Node nn = node;
                                  readActions.add(new Action(){
                                    public boolean act() throws Exception {
                                      if (!err.x && statusNode.x!=null){
                                        try{
                                          String refName;
                                          boolean[] arr = new boolean[5];
                                          for (Node m:nn.getChildren()){
                                            if (m.getValue().equals("true")){
                                              refName = m.getReferenceName();
                                              arr[Integer.parseInt(String.valueOf(refName.charAt(refName.length()-1)))] = true;
                                            }
                                          }
                                          for (Node n:statusNode.x.getChildren()){
                                            refName = n.getReferenceName();
                                            if (arr[Integer.parseInt(String.valueOf(refName.charAt(refName.length()-1)))] && !n.getValue().equals("0")){
                                              d.add(MessageType.IO, typeString+" sensor has error", cc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), linker.getIO());
                                        }
                                      }
                                      return true;
                                    }
                                  });
                                }else if (!modify && checkoutIO && refName.equals("~checkout")){
                                  final Node nn = node;
                                  readActions.add(new Action(){
                                    public boolean act() throws Exception {
                                      if (!err.x){
                                        try{
                                          boolean checked = false;
                                          boolean note = false;
                                          String refName;
                                          for (Node m:nn.getChildren()){
                                            refName = m.getReferenceName();
                                            if (refName.equals("done")){
                                              checked = m.getValue().equals("true");
                                            }else if (refName.equals("notation")){
                                              note = m.getValue().length()>0;
                                            }
                                          }
                                          if (!note && !checked){
                                            d.add(MessageType.IO, typeString+" not checked out", cc.getRelativeDisplayPath(v.root), linker.getIO());
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), linker.getIO());
                                        }
                                      }
                                      return true;
                                    }
                                  });
                                }else if (visibleIO && refName.equals("network_visible")){
                                  final Node nn = node;
                                  readActions.add(new Action(){
                                    public boolean act() throws Exception {
                                      if (!err.x){
                                        try{
                                          if (nn.getValue().equals("false")){
                                            if (modify){
                                              writeActions.add(new Action(){
                                                public boolean act() throws Exception {
                                                  try{
                                                    nn.setValue("true");
                                                    Message m = new Message(MessageType.IO, typeString+" network visibility enabled", cc.getRelativeDisplayPath(v.root), Link.createLink(UITree.GEO, cc).getURL(req));
                                                    d.reverts.add(new RevertAction(nn,"false",m));
                                                    d.add(m);
                                                  }catch(Exception e){
                                                    d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), Link.createLink(UITree.GEO, cc).getURL(req));
                                                  }
                                                  return true;
                                                }
                                              });
                                            }else{
                                              d.add(MessageType.IO, typeString+" should be network visible", cc.getRelativeDisplayPath(v.root), Link.createLink(UITree.GEO, cc).getURL(req));
                                            }
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+nn.getRelativeReferencePath(mainNode), Link.createLink(UITree.GEO, cc).getURL(req));
                                        }
                                      }
                                      return true;
                                    }
                                  });
                                }
                              }
                            }
                          }catch(Exception e){
                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), path+" : "+node.getRelativeReferencePath(mainNode), Link.createLink(UITree.GEO, c).getURL(req));
                          }
                        }
                      }
                      if (trend){
                        new Recursor(loc, new Consumer<Location>(){
                          public void accept(final Location loc){
                            /**
                             * Sets up historians for SetPoint, PhysicalPoint, SetPointAdjust, BSVI, ASVI, effective setpoints, and (possibly) BTRNs
                             * Detects errors
                             * Binary trends should have COV sampling
                             * Analog trends should have interval sampling
                             * Trending and historians should be enabled
                             * COV implies enableSamples=45 and maxSamples=100
                             * Interval implies enableSamples=65, maxSamples=144, and interval=5min
                             */
                            try{
                              if (loc.hasAspect(TrendSource.class)){
                                final Node node = loc.toNode();
                                final String nodeType = node.eval(".node-type");
                                if (trendBTRN || !nodeType.equals("230")){
                                  String refName;
                                  Node trend = null;
                                  for (Node n:node.getChildren()){
                                    refName = n.getReferenceName();
                                    if (refName.equals("trend_log")){
                                      trend = n;
                                      break;
                                    }else if (refName.equals("historical_trending_enable")){
                                      trend = node;
                                      break;
                                    }
                                  }
                                  if (trend!=null){
                                    if ((trendBTRN && nodeType.equals("230")) || loc.hasAspect(SetPoint.class) || loc.hasAspect(PhysicalPoint.class) || loc.hasAspect(SetPointAdjust.class) || nodeType.equals("286") || nodeType.equals("287") || (nodeType.equals("213") && node.hasParent() && node.getParent().eval(".node-type").equals("289"))){
                                      final boolean analog = loc.hasAspect(AnalogTrendSource.class);
                                      final Container<Boolean> cov = new Container<Boolean>(false);
                                      final Container<String> notification = new Container<String>();
                                      final Container<String> logInterval = new Container<String>();
                                      final Container<String> bufferSize = new Container<String>();
                                      final Container<Node> notificationNode = new Container<Node>();
                                      final Container<Node> logIntervalNode = new Container<Node>();
                                      final Container<Node> bufferSizeNode = new Container<Node>();
                                      for (Node n:trend.getChildren()){
                                        refName = n.getReferenceName();
                                        if (!modify && errorTrend && refName.equals("event_state")){
                                          final Node nn = n;
                                          readActions.add(new Action(){
                                            public boolean act() throws Exception {
                                              try{
                                                if (!nn.getValue().equals("0")){
                                                  d.add(MessageType.TREND, "Trend source is in error", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                }
                                              }catch(Exception e){
                                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                              }
                                              return true;
                                            }
                                          });
                                          if (!setupTrend){
                                            break;
                                          }
                                        }else if (setupTrend){
                                          if (refName.equals("cov_enable")){
                                            final Node nn = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  cov.x = nn.getValue().equals("true");
                                                  if (cov.x && analog){
                                                    cov.x = false;
                                                    if (modify){
                                                      writeActions.add(new Action(){
                                                        public boolean act() throws Exception {
                                                          try{
                                                            nn.setValue("false");
                                                            Message m = new Message(MessageType.TREND, "COV sampling disabled for analog trend source", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                            d.reverts.add(new RevertAction(nn,"true",m));
                                                            d.add(m);
                                                          }catch(Exception e){
                                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                          }
                                                          return true;
                                                        }
                                                      });
                                                    }else{
                                                      d.add(MessageType.TREND, "Analog trend source should not sample on COV", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                    }
                                                  }else if (!cov.x && !analog){
                                                    cov.x = true;
                                                    if (modify){
                                                      writeActions.add(new Action(){
                                                        public boolean act() throws Exception {
                                                          try{
                                                            nn.setValue("true");
                                                            Message m = new Message(MessageType.TREND, "COV sampling enabled for binary trend source", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                            d.reverts.add(new RevertAction(nn,"false",m));
                                                            d.add(m);
                                                          }catch(Exception e){
                                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                          }
                                                          return true;
                                                        }
                                                      });
                                                    }else{
                                                      d.add(MessageType.TREND, "Binary trend source should sample on COV", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                    }
                                                  }
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }else if (refName.equals("interval_enable")){
                                            final Node nn = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  boolean interval = nn.getValue().equals("true");
                                                  if (interval && !analog){
                                                    if (modify){
                                                      writeActions.add(new Action(){
                                                        public boolean act() throws Exception {
                                                          try{
                                                            nn.setValue("false");
                                                            Message m = new Message(MessageType.TREND, "Interval sampling disabled for binary trend source", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                            d.reverts.add(new RevertAction(nn,"true",m));
                                                            d.add(m);
                                                          }catch(Exception e){
                                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                          }
                                                          return true;
                                                        }
                                                      });
                                                    }else{
                                                      d.add(MessageType.TREND, "Binary trend source should not sample on interval", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                    }
                                                  }else if (!interval && analog){
                                                    if (modify){
                                                      writeActions.add(new Action(){
                                                        public boolean act() throws Exception {
                                                          try{
                                                            nn.setValue("true");
                                                            Message m = new Message(MessageType.TREND, "Interval sampling enabled for analog trend source", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                            d.reverts.add(new RevertAction(nn,"false",m));
                                                            d.add(m);
                                                          }catch(Exception e){
                                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                          }
                                                          return true;
                                                        }
                                                      });
                                                    }else{
                                                      d.add(MessageType.TREND, "Analog trend source should sample on interval", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                    }
                                                  }
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }else if (refName.equals("historical_trending_enable")){
                                            final Node nn = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  if (nn.getValue().equals("false")){
                                                    if (modify){
                                                      writeActions.add(new Action(){
                                                        public boolean act() throws Exception {
                                                          try{
                                                            nn.setValue("true");
                                                            Message m = new Message(MessageType.TREND, "Historian enabled", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                            d.reverts.add(new RevertAction(nn,"false",m));
                                                            d.add(m);
                                                          }catch(Exception e){
                                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                          }
                                                          return true;
                                                        }
                                                      });
                                                    }else{
                                                      d.add(MessageType.TREND, "Historian should be enabled", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                    }
                                                  }
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }else if (refName.equals("log_enable")){
                                            final Node nn = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  if (nn.getValue().equals("false")){
                                                    if (modify){
                                                      writeActions.add(new Action(){
                                                        public boolean act() throws Exception {
                                                          try{
                                                            nn.setValue("true");
                                                            Message m = new Message(MessageType.TREND, "Trend log enabled", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                            d.reverts.add(new RevertAction(nn,"false",m));
                                                            d.add(m);
                                                          }catch(Exception e){
                                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                          }
                                                          return true;
                                                        }
                                                      });
                                                    }else{
                                                      d.add(MessageType.TREND, "Trend log should be enabled", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                    }
                                                  }
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }else if (refName.equals("notification_threshold")){
                                            notificationNode.x = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  notification.x = notificationNode.x.getValue();
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+notificationNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }else if (refName.equals("log_interval")){
                                            logIntervalNode.x = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  logInterval.x = logIntervalNode.x.getValue();
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+logIntervalNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }else if (refName.equals("buffer_size")){
                                            bufferSizeNode.x = n;
                                            readActions.add(new Action(){
                                              public boolean act() throws Exception {
                                                try{
                                                  bufferSize.x = bufferSizeNode.x.getValue();
                                                }catch(Exception e){
                                                  d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+bufferSizeNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                }
                                                return true;
                                              }
                                            });
                                          }
                                        }
                                      }
                                      if (setupTrend){
                                        readActions.add(new Action(){
                                          public boolean act() throws Exception {
                                            if (cov.x){
                                              if (notification.x!=null && !notification.x.equals("45")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        notificationNode.x.setValue("45");
                                                        Message m = new Message(MessageType.TREND, "Historian enable samples changed from "+notification.x+" to 45", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                        d.reverts.add(new RevertAction(notificationNode.x,notification.x,m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+notificationNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.TREND, "Historian enable samples should be changed from "+notification.x+" to 45", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                }
                                              }
                                              if (bufferSize.x!=null && !bufferSize.x.equals("100")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        bufferSizeNode.x.setValue("100");
                                                        Message m = new Message(MessageType.TREND, "Historian max samples changed from "+bufferSize.x+" to 100", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                        d.reverts.add(new RevertAction(bufferSizeNode.x,bufferSize.x,m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+bufferSizeNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.TREND, "Historian max samples should be changed from "+bufferSize.x+" to 100", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                }
                                              }
                                            }else{
                                              if (notification.x!=null && !notification.x.equals("65")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        notificationNode.x.setValue("65");
                                                        Message m = new Message(MessageType.TREND, "Historian enable samples changed from "+notification.x+" to 65", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                        d.reverts.add(new RevertAction(notificationNode.x,notification.x,m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+notificationNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.TREND, "Historian enable samples should be changed from "+notification.x+" to 65", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                }
                                              }
                                              if (bufferSize.x!=null && !bufferSize.x.equals("144")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        bufferSizeNode.x.setValue("144");
                                                        Message m = new Message(MessageType.TREND, "Historian max samples changed "+bufferSize.x+" to 144", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                        d.reverts.add(new RevertAction(bufferSizeNode.x,bufferSize.x,m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+bufferSizeNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.TREND, "Historian max samples should be changed "+bufferSize.x+" to 144", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                }
                                              }
                                              if (logInterval.x!=null && !logInterval.x.equals("30000")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        logIntervalNode.x.setValue("30000");
                                                        Message m = new Message(MessageType.TREND, "Historian interval changed to 5 minutes", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                        d.reverts.add(new RevertAction(logIntervalNode.x,logInterval.x,m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+logIntervalNode.x.getRelativeReferencePath(node), linker.getTrend());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.TREND, "Historian interval should be changed to 5 minutes", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                }
                                              }
                                            }
                                            return true;
                                          }
                                        });
                                      }
                                    }else if (setupTrend){
                                      for (Node n:trend.getChildren()){
                                        if (n.getReferenceName().equals("log_enable")){
                                          final Node nn = n;
                                          readActions.add(new Action(){
                                            public boolean act() throws Exception {
                                              try{
                                                if (nn.getValue().equals("true")){
                                                  if (modify){
                                                    writeActions.add(new Action(){
                                                      public boolean act() throws Exception {
                                                        try{
                                                          nn.setValue("false");
                                                          Message m = new Message(MessageType.TREND, "Trend log disabled", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                          d.reverts.add(new RevertAction(nn,"true",m));
                                                          d.add(m);
                                                        }catch(Exception e){
                                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                                        }
                                                        return true;
                                                      }
                                                    });
                                                  }else{
                                                    d.add(MessageType.TREND, "Trend log should be disabled", loc.getRelativeDisplayPath(v.root), linker.getTrend());
                                                  }
                                                }
                                              }catch(Exception e){
                                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getTrend());
                                              }
                                              return true;
                                            }
                                          });
                                          break;
                                        }
                                      }
                                    }
                                  }
                                }
                              }
                            }catch(Exception e){
                              try{
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), linker.getTrend());
                              }catch(Exception ee){
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), null);
                              }
                            }
                          }
                        }).start();
                      }
                      if (net && !modify){
                        new Recursor(loc, new Consumer<Location>(){
                          public void accept(final Location loc){
                            /**
                             * Look for network point errors
                             */
                            try{
                              if (loc.hasAspect(NetworkIO.class)){
                                final Node node = loc.toNode();
                                final Container<Boolean> valid = new Container<Boolean>(true);
                                final Container<Boolean> error = new Container<Boolean>(false);
                                final Container<Boolean> invalidAddress = new Container<Boolean>(false);
                                String refName;
                                for (Node n:node.getChildren()){
                                  refName = n.getReferenceName();
                                  if (refName.equals("bp_error")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          String val = nn.getValue();
                                          if (val.equals("1")){
                                            error.x = true;
                                          }else if (!val.equals("0")){
                                            d.add(MessageType.NETWORK, "Network point is in error", loc.getRelativeDisplayPath(v.root), linker.getNetwork());
                                            error.x = true;
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getNetwork());
                                        }
                                        return true;
                                      }
                                    });
                                  }else if (refName.equals("valid")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          valid.x = nn.getDisplayValue().equalsIgnoreCase("true");
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getNetwork());
                                        }
                                        return true;
                                      }
                                    });
                                  }else if (refName.equals("address")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          String val = nn.getValue();
                                          invalidAddress.x = val.length()==0 || val.equals("bacnet://") || val.equals("exp:");
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getNetwork());
                                        }
                                        return true;
                                      }
                                    });
                                  }else if (refName.equals("com_enabled")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          error.x|=nn.getValue().equals("false");
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getNetwork());
                                        }
                                        return true;
                                      }
                                    });
                                  }
                                }
                                readActions.add(new Action(){
                                  public boolean act() throws Exception {
                                    if (!error.x && (!valid.x || invalidAddress.x)){
                                      //Known Bug - the link doesn't work properly - as far as I can tell, this is a problem with the addon API
                                      d.add(MessageType.NETWORK, "Network point output is invalid", loc.getRelativeDisplayPath(v.root), linker.getNetwork());
                                    }
                                    return true;
                                  }
                                });
                              }
                            }catch(Exception e){
                              try{
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), linker.getNetwork());
                              }catch(Exception ee){
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), null);
                              }
                            }
                          }
                        }).start();
                      }
                      if (alarm){
                        new Recursor(loc, new Consumer<Location>(){
                          public void accept(final Location loc){
                            /**
                             * Completely ignores BBO microblocks
                             * Everything should be network visible
                             * Nothing should require acknowledgement
                             * Alarm category should be selected
                             * BALM alarms should be potential alarm sources
                             */
                            try{
                              if (loc.hasAspect(AlarmSource.class)){
                                final Node node = loc.toNode();
                                final String nodeType = node.eval(".node-type");
                                if (!nodeType.equals("215")){
                                  final boolean BALM = nodeType.equals("268");
                                  final Container<Boolean> potAlarmSource = new Container<Boolean>(false);
                                  Collection<Node> children = node.getChildren();
                                  for (Node n:children){
                                    if (n.getReferenceName().equals("~event_is_alarm_source")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            potAlarmSource.x = nn.getValue().equals("true");
                                            if (BALM && balmPotAlarm && !potAlarmSource.x){
                                              if (modify){
                                                potAlarmSource.x = true;
                                                writeActions.add(new Action(){
                                                  public boolean act() throws Exception {
                                                    try{
                                                      nn.setValue("true");
                                                      Message m = new Message(MessageType.ALARM, "Potential alarm source enabled for BALM alarm", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                                      d.reverts.add(new RevertAction(nn,"false",m));
                                                      d.add(m);
                                                    }catch(Exception e){
                                                      d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                                    }
                                                    return true;
                                                  }
                                                });
                                              }else{
                                                d.add(MessageType.ALARM, "BALM alarm should be a potential alarm source", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                              }
                                            }
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                          }
                                          return true;
                                        }
                                      });
                                      break;
                                    }
                                  }
                                  String refName;
                                  for (Node n:children){
                                    refName = n.getReferenceName();
                                    if (visibleAlarm && refName.equals("network_visible")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            if (nn.getValue().equals("false")){
                                              if (modify){
                                                writeActions.add(new Action(){
                                                  public boolean act() throws Exception {
                                                    try{
                                                      nn.setValue("true");
                                                      Message m = new Message(MessageType.ALARM, "Alarm network visibility enabled", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                                      d.reverts.add(new RevertAction(nn,"false",m));
                                                      d.add(m);
                                                    }catch(Exception e){
                                                      d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                                    }
                                                    return true;
                                                  }
                                                });
                                              }else{
                                                d.add(MessageType.ALARM, "Alarm should be network visible", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                              }
                                            }
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (ackAlarm && refName.equals("~event_return_acked")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          if (potAlarmSource.x){
                                            try{
                                              if (nn.getValue().equals("true")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        nn.setValue("false");
                                                        Message m = new Message(MessageType.ALARM, "Alarm return acknowledgement disabled", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                                        d.reverts.add(new RevertAction(nn,"true",m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.ALARM, "Alarm should not require return acknowledgement", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                                }
                                              }
                                            }catch(Exception e){
                                              d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                            }
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (ackAlarm && refName.equals("~event_alarm_acked")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          if (potAlarmSource.x){
                                            try{
                                              if (nn.getValue().equals("true")){
                                                if (modify){
                                                  writeActions.add(new Action(){
                                                    public boolean act() throws Exception {
                                                      try{
                                                        nn.setValue("false");
                                                        Message m = new Message(MessageType.ALARM, "Alarm acknowledgement disabled", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                                        d.reverts.add(new RevertAction(nn,"true",m));
                                                        d.add(m);
                                                      }catch(Exception e){
                                                        d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                                      }
                                                      return true;
                                                    }
                                                  });
                                                }else{
                                                  d.add(MessageType.ALARM, "Alarm should not require acknowledgement", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                                }
                                              }
                                            }catch(Exception e){
                                              d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                            }
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (!modify && catAlarm && refName.equals("~event_category")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          if (potAlarmSource.x){
                                            try{
                                              String val = nn.getValue();
                                              if (val.equals("other") || val.equals("unknown")){
                                                d.add(MessageType.ALARM, "Alarm category is not selected", loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                                              }
                                            }catch(Exception e){
                                              d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getAlarm());
                                            }
                                          }
                                          return true;
                                        }
                                      });
                                    }
                                  }
                                }
                              }
                            }catch(Exception e){
                              try{
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), linker.getAlarm());
                              }catch(Exception ee){
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), null);
                              }
                            }
                          }
                        }).start();
                      }
                      if (io){
                        /**
                         * Want input_number!=0 and 0<=expander_number<=9
                         * Compares tuples (input,output,expander,binary/analog) to check for duplicates [and ignores points with input_type="Flow Input"]
                         * All points should be checked out or have a comment
                         * All values should be unlocked
                         * Checks for errors
                         */
                        final ArrayList<Point> duplicates = new ArrayList<Point>();
                        new Recursor(loc, new Consumer<Location>(){
                          public void accept(final Location loc){
                            try{
                              if (loc.hasAspect(PhysicalPoint.class)){
                                final Node node = loc.toNode();
                                final Container<Integer> inputNumber = new Container<Integer>(-1);
                                final Container<Integer> outputNumber = new Container<Integer>(-1);
                                final Container<Integer> expanderNumber = new Container<Integer>(-1);
                                final Container<Boolean> analog = new Container<Boolean>(false);
                                final Container<Boolean> flowInput = new Container<Boolean>(false);
                                String refName;
                                for (Node n:node.getChildren()){
                                  refName = n.getReferenceName();
                                  if (!modify && checkoutIO && refName.equals("~checkout")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          boolean checked = false;
                                          boolean note = false;
                                          String refName;
                                          for (Node m:nn.getChildren()){
                                            refName = m.getReferenceName();
                                            if (refName.equals("done")){
                                              checked = m.getValue().equals("true");
                                            }else if (refName.equals("notation")){
                                              note = m.getValue().length()>0;
                                            }
                                          }
                                          if (!note && !checked){
                                            d.add(MessageType.IO, "I/O point not checked out", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                        }
                                        return true;
                                      }
                                    });
                                  }else if (lockedIO && refName.equals("locked")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          if (nn.getValue().equals("true")){
                                            if (modify){
                                              writeActions.add(new Action(){
                                                public boolean act() throws Exception {
                                                  try{
                                                    nn.setValue("false");
                                                    Message m = new Message(MessageType.IO, "I/O point unlocked", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                                    d.reverts.add(new RevertAction(nn,"true",m));
                                                    d.add(m);
                                                  }catch(Exception e){
                                                    d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                                  }
                                                  return true;
                                                }
                                              });
                                            }else{
                                              d.add(MessageType.IO, "I/O point locked", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                        }
                                        return true;
                                      }
                                    });
                                  }else if (visibleIO && refName.equals("network_visible")){
                                    final Node nn = n;
                                    readActions.add(new Action(){
                                      public boolean act() throws Exception {
                                        try{
                                          if (nn.getValue().equals("false")){
                                            if (modify){
                                              writeActions.add(new Action(){
                                                public boolean act() throws Exception {
                                                  try{
                                                    nn.setValue("true");
                                                    Message m = new Message(MessageType.IO, "I/O point network visibility enabled", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                                    d.reverts.add(new RevertAction(nn,"false",m));
                                                    d.add(m);
                                                  }catch(Exception e){
                                                    d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                                  }
                                                  return true;
                                                }
                                              });
                                            }else{
                                              d.add(MessageType.IO, "I/O point should be network visible", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }
                                        }catch(Exception e){
                                          d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                        }
                                        return true;
                                      }
                                    });
                                  }else if (!modify && errorIO && refName.equals("status_flags")){
                                    for (Node m:n.getChildren()){
                                      refName = m.getReferenceName();
                                      if (refName.equals("fault")){
                                        final Node nn = m;
                                        readActions.add(new Action(){
                                          public boolean act() throws Exception {
                                            try{
                                              if (nn.getValue().equals("true")){
                                                d.add(MessageType.IO, "I/O point is in error", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                              }
                                            }catch(Exception e){
                                              d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                            }
                                            return true;
                                          }
                                        });
                                        break;
                                      }
                                    }
                                  }else if (!modify && duplicateIO){
                                    if (refName.equals("input_number")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            inputNumber.x = Integer.parseInt(nn.getValue());
                                            if (inputNumber.x==0){
                                              d.add(MessageType.IO, "I/O point input number is 0", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (refName.equals("output_number")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            outputNumber.x = Integer.parseInt(nn.getValue());
                                            if (outputNumber.x==0){
                                              d.add(MessageType.IO, "I/O point output number is 0", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (refName.equals("expander_number")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            expanderNumber.x = Integer.parseInt(nn.getValue());
                                            if (expanderNumber.x<0 || expanderNumber.x>9){
                                              d.add(MessageType.IO, "I/O point expander number is not within acceptable range (0-9)", loc.getRelativeDisplayPath(v.root), linker.getIO());
                                            }
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (refName.equals("object_type")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            analog.x = nn.getDisplayValue().startsWith("Analog");
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                          }
                                          return true;
                                        }
                                      });
                                    }else if (refName.equals("input_type")){
                                      final Node nn = n;
                                      readActions.add(new Action(){
                                        public boolean act() throws Exception {
                                          try{
                                            flowInput.x = nn.getDisplayValue().equals("Flow Input");
                                          }catch(Exception e){
                                            d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root)+" : "+nn.getRelativeReferencePath(node), linker.getIO());
                                          }
                                          return true;
                                        }
                                      });
                                    }
                                  }
                                }
                                if (!modify && duplicateIO){
                                  readActions.add(new Action(){
                                    public boolean act(){
                                      if (!flowInput.x){
                                        duplicates.add(new Point(inputNumber.x,outputNumber.x,expanderNumber.x,analog.x,loc.getDisplayName()));
                                      }
                                      return true;
                                    }
                                  });
                                }
                              }
                            }catch(Exception e){
                              try{
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), linker.getIO());
                              }catch(Exception ee){
                                d.add(MessageType.ERROR, e.getClass().getSimpleName(), loc.getRelativeDisplayPath(v.root), null);
                              }
                            }
                          }
                        }).start();
                        if (!modify && duplicateIO){
                          readActions.add(new Action(){
                            public boolean act() throws Exception {
                              duplicates.sort(null);
                              int len = duplicates.size();
                              Point p,q;
                              StringBuilder sb = new StringBuilder();
                              for (int i=0,j;i<len;){
                                p = duplicates.get(i++);
                                sb.setLength(0);
                                sb.append(p.name);
                                for (j=1;i<len;i++){
                                  q = duplicates.get(i);
                                  if (p.equals(q)){
                                    ++j;
                                    sb.append(", ");
                                    sb.append(q.name);
                                  }else{
                                    break;
                                  }
                                }
                                if (j>1){
                                  d.add(MessageType.IO, "Duplicates: "+sb.toString(), path, linker.getIO());
                                }
                              }
                              return true;
                            }
                          });
                        }
                      }
                      readActions.add(null);
                    }
                    ++v.pos;
                    if (System.currentTimeMillis()>time){
                      return false;
                    }
                  }
                  return true;
                }
              });
              if (ret){
                if (readActions.size()==0){
                  d.percentComplete = 100;
                  return true;
                }else{
                  ++v.phase;
                  d.percentComplete = 20;
                  v.cap = readActions.size();
                  v.pos = 0;
                  v.actIter = readActions.iterator();
                  return false;
                }
              }else{
                d.percentComplete = 5+15*v.pos/v.cap;
                return false;
              }
            }else if (v.phase==4){// 20% -> 85% || 100%
              //Execute readActions which will form a list of writeActions if modifications are required
              boolean ret = con.runReadAction(FieldAccessFactory.newFieldAccess(), new ReadActionResult<Boolean>(){
                public Boolean execute(SystemAccess sys) throws Exception {
                  long time = System.currentTimeMillis()+ProgressHandler.TIMEOUT;
                  Action a;
                  while (v.actIter.hasNext()){
                    ++v.pos;
                    a = v.actIter.next();
                    if (a!=null && !a.act()){
                      while (v.actIter.hasNext() && v.actIter.next()!=null){}
                      if (!v.actIter.hasNext()){
                        return true;
                      }
                    }
                    if (System.currentTimeMillis()>time){
                      return false;
                    }
                  }
                  return true;
                }
              });
              if (ret){
                if (!modify || writeActions.size()==0){
                  d.percentComplete = 100;
                  return true;
                }else{
                  ++v.phase;
                  d.percentComplete = 85;
                  v.cap = writeActions.size();
                  v.pos = 0;
                  v.actIter = writeActions.iterator();
                  return false;
                }
              }else{
                d.percentComplete = 20+(modify?65:80)*v.pos/v.cap;
                return false;
              }
            }else if (v.phase==5){// 85% -> 100%
              //Execute writeActions
              con.runWriteAction(FieldAccessFactory.newFieldAccess(), "Enforcing commissioning standards.", new WriteAction(){
                public void execute(WritableSystemAccess sys) throws Exception {
                  long time = System.currentTimeMillis()+ProgressHandler.TIMEOUT;
                  Action a;
                  while (v.actIter.hasNext()){
                    ++v.pos;
                    a = v.actIter.next();
                    if (a!=null && !a.act()){
                      while (v.actIter.hasNext() && v.actIter.next()!=null){}
                      if (!v.actIter.hasNext()){
                        v.bool = true;
                        return;
                      }
                    }
                    if (System.currentTimeMillis()>time){
                      v.bool = false;
                      return;
                    }
                  }
                  v.bool = true;
                  return;
                }
              });
              if (v.bool){
                d.percentComplete = 100;
                return true;
              }else{
                d.percentComplete = 85+15*v.pos/v.cap;
                return false;
              }
            }else{
              d.add(MessageType.ERROR, "Invalid phase transition.", null, null);
              d.percentComplete = 100;
              return true;
            }
          }
        });
        dis.username = con.getOperator().getLoginName();
        token = String.valueOf(dis.getToken());
      }else{
        try{
          dis = ProgressHandler.get(Integer.parseInt(token));
          if (dis==null){
            res.sendError(404, "Invalid token. Note that tokens expire an hour after task completion.");
            return;
          }else if (dis.archived.get()){
            res.sendError(404, "Cannot view archived task.");
            return;
          }
        }catch(NumberFormatException e){
          res.sendError(400, "Unable to parse \"token\" parameter.");
          return;
        }
      }
      int percentComplete = dis.percentComplete;
      if (percentComplete>=100 && !dis.completed){
        percentComplete = 99;
      }else if (dis.completed){
        percentComplete = 100;
      }
      StringBuilder sb = new StringBuilder(4096);
      sb.append("<!DOCTYPE html>\n");
      sb.append("<html>\n");
      sb.append("<head>\n");
      sb.append("<title>\n");
      sb.append("ACES Commissioner\n");
      sb.append("</title>\n");
      sb.append("<style>\n");
      sb.append("progress {\n");
      sb.append("width:75vw;\n");
      sb.append("height:40px;\n");
      sb.append("}\n");
      sb.append("</style>\n");
      sb.append("<script>\n");
      sb.append("function update(){\n");
      sb.append("let req = new XMLHttpRequest()\n");
      sb.append("req.open(\"POST\", \"/"+MainGUI.name+"/Updater\", true)\n");
      sb.append("req.setRequestHeader(\"content-type\", \"application/x-www-form-urlencoded\")\n");
      sb.append("req.onreadystatechange = function(){\n");
      sb.append("if (this.readyState==4){\n");
      sb.append("switch(this.status){\n");
      sb.append("case 200:\n");
      sb.append("let x = this.responseText.charCodeAt(0)\n");
      sb.append("if (x<0){\n");
      sb.append("x = 0\n");
      sb.append("}else if (x>100){\n");
      sb.append("x = 100\n");
      sb.append("}\n");
      sb.append("setPercent(x)\n");
      sb.append("break\n");
      sb.append("case 401:\n");
      sb.append("msg.innerHTML = \"You have been logged out due to inactivity.\"\n");
      sb.append("break\n");
      sb.append("case 404:\n");
      sb.append("msg.innerHTML = \"Task either is archived or does not exist.\"\n");
      sb.append("break\n");
      sb.append("case 500:\n");
      sb.append("resultForm.submit()\n");
      sb.append("break\n");
      sb.append("default:\n");
      sb.append("msg.innerHTML = \"Unexpected Error: \"+this.status\n");
      sb.append("}\n");
      sb.append("}\n");
      sb.append("}\n");
      sb.append("req.send(\"token="+token+"\")\n");
      sb.append("}\n");
      sb.append("function setPercent(x){\n");
      sb.append("if (x==100){\n");
      sb.append("resultForm.submit();\n");
      sb.append("}else{\n");
      sb.append("progressBar.value = x\n");
      sb.append("displayPercent.innerHTML = x+'%'\n");
      sb.append("setTimeout(update, 1000)\n");
      sb.append("}\n");
      sb.append("}\n");
      sb.append("function cancel(){\n");
      sb.append("cancelButton.disabled = true\n");
      sb.append("msg.innerHTML = \"Cancelling task...\"\n");
      sb.append("let req = new XMLHttpRequest()\n");
      sb.append("req.open(\"POST\", \"/"+MainGUI.name+"/Cancel\", true)\n");
      sb.append("req.setRequestHeader(\"content-type\", \"application/x-www-form-urlencoded\")\n");
      sb.append("req.send(\"token="+token+"\")\n");
      sb.append("}\n");
      sb.append("</script>\n");
      sb.append("</head>\n");
      sb.append("<body>\n");
      sb.append("<div style=\"text-align:center\">\n");
      sb.append("<h1>Task Executing</h1>\n");
      sb.append("<button onClick=\"cancel()\" id=\"cancelButton\">Cancel</button><br><br>\n");
      sb.append("<progress max=\"100\" id=\"progressBar\"></progress>\n");
      sb.append("<h3 id=\"displayPercent\"></h3>\n");
      sb.append("<form action=\"Results\" method=\"POST\" id=\"resultForm\">\n");
      sb.append("<input type=\"hidden\" name=\"token\" value=\""+token+"\" />\n");
      sb.append("</form>\n");
      sb.append("<h3 id=\"msg\"></h3>\n");
      sb.append("</div>\n");
      sb.append("<script>\n");
      sb.append("setPercent("+percentComplete+")\n");
      sb.append("</script>\n");
      sb.append("</body>\n");
      sb.append("</html>\n");
      PrintWriter out = res.getWriter();
      res.setContentType("text/html");
      out.print(sb.toString());
      out.flush();
    }catch(Exception e){
      PrintWriter out = res.getWriter();
      res.setContentType("text/plain");
      e.printStackTrace(out);
      out.flush();
    }
  }
  private static void recurse(Vars v, Display d, Location loc, ArrayList<Location> equipment, boolean schedules, Collection<ScheduleCategory<Boolean>> scheduleBool) throws Exception {
    LocationType t = loc.getType();
    if (schedules){
      scheduleBlock:{
        try{
          if (loc.hasAspect(Schedulable.class)){
            Schedulable sch = loc.getAspect(Schedulable.class);
            for (ScheduleCategory<Boolean> cat:scheduleBool){
              for (Schedule<Boolean> s:sch.getSchedules(cat)){
                ScheduleTemplate<Boolean> template = s.getTemplate();
                if (s.getPriority().getIndex()==0 && template instanceof Weekly){
                  Weekly<Boolean> weekly = (Weekly<Boolean>)template;
                  if (weekly.getDaysOfWeek().containsAll(EnumSet.allOf(DayOfWeek.class))){
                    for (SchedulePeriod<Boolean> period:weekly.getPeriods()){
                      SimpleTime start = period.getStartTime();
                      SimpleTime end = period.getEndTime();
                      if (start.getHour()==0 && start.getMinute()==0 && start.getSecond()==0 && start.getHundredth()==0 && end.getHour()==0 && end.getMinute()==0 && end.getSecond()==0 && end.getHundredth()==0){
                        d.add(MessageType.SCHEDULE, "Bad schedule detected", loc.getRelativeDisplayPath(v.root), Link.createLink(UITree.GEO, loc, "schedules", "occupancy", "default", "config").getURL(v.req));
                        break scheduleBlock;
                      }
                    }
                  }
                }
              }
            }
          }
        }catch(Exception e){
          d.add(MessageType.ERROR, "Error occurred while checking for bad schedules.", loc.getRelativeDisplayPath(v.root), Link.createLink(UITree.GEO, loc, "schedules", "occupancy", "default", "config").getURL(v.req));
        }
      }
    }
    if (t==LocationType.Equipment){
      equipment.add(loc);
    }else{
      for (Location l:loc.getChildren()){
        t = l.getType();
        if (t==LocationType.Equipment || t==LocationType.Area || t==LocationType.System || t==LocationType.Directory){
          recurse(v,d,l,equipment,schedules,scheduleBool);
        }
      }
    }
  }
}
class Vars {
  public volatile Location root = null;
  public volatile HttpServletRequest req = null;
  public volatile int phase = 1;
  public volatile Iterator<Action> actIter = null;
  public volatile Iterator<Location> locIter = null;
  public volatile int cap = 0;
  public volatile int pos = 0;
  public volatile boolean bool = false;
}
abstract class Action {
  /**
   * @return {@code true} to continue executing; {@code false} to cancel all pending actions up to the next null action.
   */
  public abstract boolean act() throws Exception;
}
/**
 * Recurses into all sublocations of a given location.
 * The consumer accepts all but the given root location.
 */
class Recursor {
  private volatile Location start;
  private volatile Consumer<Location> con;
  public Recursor(Location start, Consumer<Location> con){
    this.start = start;
    this.con = con;
  }
  public void start(){
    recurse(start);
  }
  private void recurse(Location loc){
    for (Location l:loc.getChildren()){
      con.accept(l);
      recurse(l);
    }
  }
}
class LinkHandler {
  private String net = null;
  private String io = null;
  private String alarm = null;
  private String trend = null;
  private Location loc;
  private HttpServletRequest req;
  public LinkHandler(Location loc, HttpServletRequest req){
    this.loc = loc;
    this.req = req;
  }
  public String getNetwork() throws LinkException {
    if (net==null){
      net = Link.createLink(UITree.GEO, loc, "properties", "default", "equipment", "network_points").getURL(req);
    }
    return net;
  }
  public String getIO() throws LinkException {
    if (io==null){
      io = Link.createLink(UITree.GEO, loc, "properties", "default", "equipment", "io_points").getURL(req);
    }
    return io;
  }
  public String getAlarm() throws LinkException {
    if (alarm==null){
      alarm = Link.createLink(UITree.GEO, loc, "properties", "default", "equipment", "events").getURL(req);
    }
    return alarm;
  }
  public String getTrend() throws LinkException {
    if (trend==null){
      trend = Link.createLink(UITree.GEO, loc, "properties", "default", "equipment", "trends").getURL(req);
    }
    return trend;
  }
}
/**
 * Utilitity class for detecting duplicate I/O points
 */
class Point implements Comparable<Point> {
  int x,y,z;
  boolean analog;
  String name = null;
  public Point(int x, int y, int z, boolean analog, String name){
    this.x = x;
    this.y = y;
    this.z = z;
    this.analog = analog;
    this.name = name;
  }
  @Override
  public boolean equals(Object obj){
    if (obj instanceof Point){
      Point p = (Point)obj;
      return x==p.x && y==p.y && z==p.z && analog==p.analog;
    }else{
      return false;
    }
  }
  @Override
  public int compareTo(Point p){
    if (!analog && p.analog){
      return -1;
    }else if (analog && !p.analog){
      return 1;
    }
    if (x<p.x){
      return -1;
    }else if (x>p.x){
      return 1;
    }
    if (y<p.y){
      return -1;
    }else if (y>p.y){
      return 1;
    }
    if (z<p.z){
      return -1;
    }else if (z>p.z){
      return 1;
    }
    return 0;
  }
}