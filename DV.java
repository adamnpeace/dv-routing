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
            if (entry.getDestination() == destination && entry.getMetric() != INFINITY) {
                // Table has entry for given destination, forward
                return entry.getInterface();
            }
        }
        // Table has no entries for given destination
        return UNKNOWN;
    }

    public void tidyTable() {
        Vector<DVRoutingTableEntry> gc = new Vector<>();
        // Exp time, corresponding interfaces are up/down
        for (DVRoutingTableEntry entry : table) {
            if (!router.getInterfaceState(entry.getInterface())) {
                if (entry.getMetric() < INFINITY) entry.setTime(router.getCurrentTime() - 1);
                entry.setMetric(INFINITY);
            }
            if (entry.getMetric() >= INFINITY && router.getCurrentTime() - entry.getTime() > 4 * update_interval) {
                gc.add(entry);
            }
        }
        if (allow_expire) table.removeAll(gc);
    }

    public Packet generateRoutingPacket(int iface) {
        if (router.getInterfaceState(iface)) {
            RoutingPacket p = new RoutingPacket(router.getId(), Packet.BROADCAST);
            Payload payload = new Payload();
            // Occupy payload with local table
            for (DVRoutingTableEntry entry : table) {
                int metric;
                if (allow_preverse && entry.getInterface() == iface) {
                    metric = INFINITY;
                } else {
                    metric = entry.getMetric();
                }
                payload.addEntry(new DVRoutingTableEntry(entry.getDestination(), entry.getInterface(), metric, router.getCurrentTime()));

            }
            p.setPayload(payload);
            return p;
        } else {
            // Do not send if the interface is down
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
                    if (new_metric > INFINITY) new_metric = INFINITY;

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
                        if (new_metric < INFINITY) {
                            DVRoutingTableEntry rt_new = new DVRoutingTableEntry(in_entry.getDestination(), iface, new_metric, router.getCurrentTime());
                            table.add(rt_new);
                        }
                    } else if (table.get(match).getInterface() == iface && table.get(match).getMetric() < INFINITY) {
                        table.get(match).setMetric(new_metric);
                        if (new_metric >= INFINITY) table.get(match).setTime(router.getCurrentTime());
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
            // Interface is down, don't do anything
            assert true;
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
        for (DVRoutingTableEntry entry : table) {
            System.out.println(entry.toString());
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