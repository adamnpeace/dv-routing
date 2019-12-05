import java.util.Vector;

public class DV implements RoutingAlgorithm {
    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    public Router router;
    public Vector<DVRoutingTableEntry> table;
    public int update_interval;
    public boolean allow_preverse;
    public boolean allow_expire;

    public DV() {
        table = new Vector<DVRoutingTableEntry>();
    }

    public void setRouterObject(Router obj) {
        router = obj;
    }

    public void setUpdateInterval(int u) {
        update_interval = u;
    }

    public void setAllowPReverse(boolean flag) {
        allow_preverse = flag;
    }

    public void setAllowExpire(boolean flag) {
        allow_expire = flag;
    }
    
    public void initalise()
    {
    }

    public int getNextHop(int destination)
    {
        return 0;
    }

    public void tidyTable() {
    }
    
    public Packet generateRoutingPacket(int iface)
    {
        return null;
    }
    
    public void processRoutingPacket(Packet p, int iface)
    {
    }

    public void showRoutes() {
        System.out.println("Router " + router.getId());
        for (int i = 0; i < table.size(); i++) {
            System.out.println(table.get(i).toString());
        }
    }

}

class DVRoutingTableEntry implements RoutingTableEntry {
    private int dst;
    private int iface;
    private int metric;
    private int time;

    public DVRoutingTableEntry(int d, int i, int m, int t) {
        dst = d;
        iface = i;
        metric = m;
        time = t;
    }

    public int getDestination() {
        return dst;
    }

    public void setDestination(int d) {
        dst = d;
    }

    public int getInterface() {
        return iface;
    }

    public void setInterface(int i) {
        iface = i;
    }

    public int getMetric() {
        return metric;
    }

    public void setMetric(int m) {
        metric = m;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int t) {
        time = t;
    }

    public String toString() {
        return "d " + dst + " i " + iface + " m " + metric;
    }
}