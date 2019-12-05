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
//        System.out.println("Tidying tables on " + router.getId());
        // Exp time, corresponding interfaces are up/down
        for (int i = 0; i < table.size(); i++) {
            if (!router.getInterfaceState(table.get(i).getInterface())) {
                table.get(i).setMetric(INFINITY);
//                System.out.println("Just set " + table.get(i).toString());
            }

        }
    }

    public Packet generateRoutingPacket(int iface) {
        if (router.getInterfaceState(iface)) {
            RoutingPacket p = new RoutingPacket(router.getId(), Packet.BROADCAST);
            Payload payload = new Payload();
            // Occupy payload with local table
            for (DVRoutingTableEntry entry : table) {
                payload.addEntry(entry);
            }
            // Should we send if iface is up?
            p.setPayload(payload);
            return p;
        } else {
            return null;
        }
    }

    public void processRoutingPacket(Packet p, int iface) {
        if (router.getInterfaceState(iface)) {
            // The interface is up
            if (p.getType() == Packet.ROUTING) {
                // Increment the Metric //
                for (Object datum : p.getPayload().getData()) {
                    DVRoutingTableEntry in_entry = (DVRoutingTableEntry) datum;
                    int new_metric = in_entry.getMetric() + router.getInterfaceWeight(iface);
                    // Search for destination in entry table //

                    // Finds a local entry with matching dst
                    int match = -1;
                    for (int i = 0; i < table.size(); i++) {
                        if (table.get(i).getDestination() == in_entry.getDestination()) {
                            match = i;
                        }
                    }

                    if (match < 0) {
                        // No entry found in local table with this destination
                        DVRoutingTableEntry rt_new = new DVRoutingTableEntry(in_entry.getDestination(), iface, new_metric, router.getCurrentTime());
                        table.add(rt_new);
                    } else if (table.get(match).getInterface() == iface) {
                        table.get(match).setMetric(new_metric);
                    } else if (new_metric < table.get(match).getMetric()) {
                        // Found local entry has greater metric than incoming entry
                        table.get(match).setMetric(new_metric);
                        table.get(match).setInterface(iface);
                    }

                }


            }
            if (p.getType() == Packet.DATA) {
                int out_iface = getNextHop(p.getDestination());
                if (out_iface > -1) router.send(p, out_iface);
            }
        } else {
//            System.out.println("LINK " + iface + " DOWN on router " + router.getId());
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