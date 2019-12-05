import java.util.Collections;
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

    public void initalise() {
        DVRoutingTableEntry localEntry = new DVRoutingTableEntry(router.getId(), LOCAL, 0, router.getCurrentTime());
        table.add(localEntry);
    }

    public int getNextHop(int destination) {
        if (destination == router.getId()) {
            // Destination is this router
            return LOCAL;
        }
        for (DVRoutingTableEntry entry : table) {
            if (entry.getDestination() == destination) {
                // Table has entry for given destination, forward
                return entry.getInterface();
            }
        }
        // Table has no entries for given destination
        return UNKNOWN;
    }

    public void tidyTable() {

    }

    public Packet generateRoutingPacket(int iface) {
        RoutingPacket p = new RoutingPacket(router.getId(), Packet.BROADCAST);
        Payload payload = new Payload();
        // Occupy payload with local table
        for (DVRoutingTableEntry entry : table) {
            payload.addEntry(entry);
        }
        p.setPayload(payload);
        return p;
    }

    public void processRoutingPacket(Packet p, int iface) {
        if (p.getType() == Packet.ROUTING) {
            // Increment the Metric //
            for (Object datum : p.getPayload().getData()) {
                DVRoutingTableEntry in_entry = (DVRoutingTableEntry) datum;
                int new_metric = in_entry.getMetric() + router.getInterfaceWeight(iface);
                // Search for destination in entry table //
                boolean not_found = true;
                for (DVRoutingTableEntry local_entry : table) {
                    if (local_entry.getDestination() == in_entry.getDestination()) {
                        // Entry found in local table with this destination
                        not_found = false;
                        if (new_metric < local_entry.getMetric()) {
                            // Local entry has greater metric than incoming entry
                            local_entry.setMetric(new_metric);
                            local_entry.setInterface(iface);
                        }
                    }
                }

                // No entry found in local table with this destination
                if (not_found) {
                    DVRoutingTableEntry rt_new = new DVRoutingTableEntry(in_entry.getDestination(), iface, new_metric, router.getCurrentTime());
                    table.add(rt_new);
                }
            }


        }
        if (p.getType() == Packet.DATA) {
            int out_iface = getNextHop(p.getDestination());
            if (out_iface > -1) router.send(p, out_iface);
        }
    }

    public void showRoutes() {
        for (int i = 0; i < table.size(); i++) {
            for (int j = 0; j < table.size(); j++) {
                if (table.get(i).getDestination() < table.get(j).getDestination()) {
                    DVRoutingTableEntry temp = table.get(i);
                    table.set(i, table.get(j));
                    table.set(j, temp);
                }
            }
        }
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