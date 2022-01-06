import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;
import javax.servlet.*;
import com.controlj.green.addonsupport.*;
/**
 * Process queued tasks using a single-thread which is created/destroyed as needed.
 * To conserve resources, the thread commits suicide when the queue is empty.
 * The thread is re-initialized when a new task is appended to the queue.
 */
public class ProgressHandler implements ServletContextListener {
  /** The database won't be locked for much longer than this timeout period in milliseconds */
  public final static long TIMEOUT = 1000L;
  public volatile static Path folder = null;
  public volatile static Path dataFile = null;
  private final static ReentrantReadWriteLock mapLock = new ReentrantReadWriteLock();
  private final static TreeMap<Integer,Display> map = new TreeMap<Integer,Display>();
  private final static Queue<Task> queue = new LinkedList<Task>();
  private final static AtomicInteger nextID = new AtomicInteger();
  private volatile static boolean running = false;
  private volatile static boolean stop = false;
  @Override public void contextInitialized(ServletContextEvent e){
    try{
      AddOnInfo info = AddOnInfo.getAddOnInfo();
      MainGUI.name = info.getName();
      folder = info.getPrivateDir().toPath();
      dataFile = folder.resolve("archive.data");
      MainGUI.load();
      loadArchive();
    }catch(Exception err){}
  }
  private void loadArchive() throws Exception {
    mapLock.writeLock().lock();
    try{
      SerializationStream s = new SerializationStream(Files.readAllBytes(dataFile));
      Display d;
      int t;
      int high = 0;
      while (!s.end()){
        d = Display.deserialize(s);
        t = d.getToken();
        if (t>high){
          high = t;
        }
        map.put(t,d);
      }
      nextID.set(high);
    }finally{
      mapLock.writeLock().unlock();
    }
  }
  @Override public void contextDestroyed(ServletContextEvent e){
    stop = true;
    try{
      //Wait for the spawned thread to terminate.
      synchronized (ProgressHandler.class){
        while (running){
          try{
            ProgressHandler.class.wait(1000);
          }catch(Exception err){}
        }
      }
      mapLock.readLock().lock();
      synchronized (queue){
        Task t;
        while ((t=queue.poll())!=null){
          if (!t.d.completed){
            t.d.completed = true;
            t.d.stop = true;
            t.d.sort();
          }
        }
      }
      try(
        FileChannel out = FileChannel.open(dataFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        FileLock lock = out.tryLock();
      ){
        Display d;
        for (Map.Entry<Integer,Display> entry:map.entrySet()){
          d = entry.getValue();
          d.archive();
          out.write(ByteBuffer.wrap(d.serialize()));
        }
      }finally{
        mapLock.readLock().unlock();
      }
    }catch(Exception err){}
  }
  public static Display get(Integer token){
    mapLock.readLock().lock();
    try{
      return map.get(token);
    }finally{
      mapLock.readLock().unlock();
    }
  }
  public static void forEach(Consumer<Display> action){
    mapLock.readLock().lock();
    try{
      for (Map.Entry<Integer,Display> entry:map.entrySet()){
        action.accept(entry.getValue());
      }
    }finally{
      mapLock.readLock().unlock();
    }
  }
  public static Display start(boolean modify, Task task){
    if (!stop){
      Display d = new Display(nextID.incrementAndGet());
      d.modify = modify;
      {
        mapLock.writeLock().lock();
        map.put(d.getToken(), d);
        Iterator<Map.Entry<Integer,Display>> iter = map.entrySet().iterator();
        Display dis;
        while (iter.hasNext()){
          dis = iter.next().getValue();
          if (dis.isExpired()){
            dis.archive();
          }
        }
        mapLock.writeLock().unlock();
      }
      task.d = d;
      boolean b = false;
      synchronized (queue){
        queue.add(task);
        if (!running){
          running = true;
          b = true;
        }
      }
      if (!stop && b){
        new Thread(){
          public void run(){
            Task t;
            while (true){
              synchronized (queue){
                t = queue.poll();
                if (stop || t==null){
                  if (t!=null){
                    queue.add(t);
                  }
                  running = false;
                  break;
                }
              }
              if (t.d.stop){
                t.d.sort();
                t.d.completed = true;
              }else{
                try{
                  boolean finished = t.run();
                  t.d.update();
                  if (finished){
                    t.d.sort();
                    t.d.completed = true;
                  }else{
                    synchronized (queue){
                      queue.add(t);
                    }
                  }
                }catch(Exception e){
                  t.d.error = e;
                  t.d.sort();
                  t.d.completed = true;
                }
              }
            }
            if (stop){
              synchronized (ProgressHandler.class){
                ProgressHandler.class.notifyAll();
              }
            }
          }
        }.start();
      }
      return d;
    }
    return null;
  }
}