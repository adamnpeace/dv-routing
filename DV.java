import java.util.Vector;

public class DV implements RoutingAlgorithm {
    private static int LOCAL = -1;
    private static int UNKNOWN = -2;
    private static int INFINITY = 60;

    private static int GARBAGE_COLLECTOR_RATIO = 4;

    public Router router;
    private Vector<DVRoutingTableEntry> table;
    private int update_interval;
    private boolean allow_preverse;
    private boolean allow_expire;

    public DV() {
        table = new Vector<>();
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
                // Table has existing entry for given destination
                return entry.getInterface();
            }
        }
        // Table has no entries for given destination
        return UNKNOWN;
    }

    public void tidyTable() {
        // NB: All time calculations done here are essentially 1 step ahead of
        // the rest of the code, hence we remove 1 from each time calculation.

        // We will add all entries to be discarded to the following
        Vector<DVRoutingTableEntry> discard = new Vector<>();
        // Exp time, corresponding interfaces are up/down
        for (DVRoutingTableEntry entry : table) {

            // Only reset gc timer if existing entry metric is not INFINITY
            int initial_metric = entry.getMetric();
            if (initial_metric < INFINITY) entry.setTime(router.getCurrentTime() - 1);

            // If interface in entry is down, set metric to INFINITY
            if (!router.getInterfaceState(entry.getInterface())) entry.setMetric(INFINITY);

            // If timer has exceeded the allotted timeout policy, set the entry to be removed
            if (router.getCurrentTime() - entry.getTime() - 1 >= GARBAGE_COLLECTOR_RATIO * update_interval) discard.add(entry);
        }
        if (allow_expire) table.removeAll(discard);
    }

    public Packet generateRoutingPacket(int iface) {
        if (router.getInterfaceState(iface)) {
            // Link is up for given interface

            // Create a payload for outgoing packet
            Payload payload = new Payload();
            // Occupy payload with entries from local table
            for (DVRoutingTableEntry entry : table) {
                int metric;
                if (allow_preverse && entry.getInterface() == iface) {
                    // Poison Reverse: if entry uses the interface it's being sent on, poison the
                    // entry so the recipient doesn't choose to go through the current router
                    metric = INFINITY;
                } else {
                    metric = entry.getMetric();
                }
                payload.addEntry(new DVRoutingTableEntry(entry.getDestination(), entry.getInterface(), metric, router.getCurrentTime()));
            }

            // Create new routing packet with broadcast address
            RoutingPacket p = new RoutingPacket(router.getId(), Packet.BROADCAST);
            p.setPayload(payload);
            return p;
        } else {
            // Link is down for given interface, send nothing
            return null;
        }
    }

    public void processRoutingPacket(Packet p, int iface) {
        if (router.getInterfaceState(iface)) {
            // The interface is up
            if (p.getType() == Packet.ROUTING) {
                // Packet is routing type
                // Iterate over incoming entries
                for (Object datum : p.getPayload().getData()) {
                    // Cast data as DVRoutingTableEntry to use object methods
                    DVRoutingTableEntry in_entry = (DVRoutingTableEntry) datum;
                    // Increment the incoming metric
                    int new_metric = in_entry.getMetric() + router.getInterfaceWeight(iface);
                    // Limit metric value to INFINITY
                    if (new_metric > INFINITY) new_metric = INFINITY;

                    // Search for existing entry with matching destination in entry table //
                    DVRoutingTableEntry match = null;
                    for (DVRoutingTableEntry entry : table) {
                        if (entry.getDestination() == in_entry.getDestination()) match=entry;
                    }

                    if (match == null) {
                        // No existing entry found for this destination
                        // NB: This if statement must be nested to avoid NPE
                        if (new_metric < INFINITY) {
                            // New entry is for a valid route
                            // NB: Copy of entry must be made here
                            DVRoutingTableEntry rt_new = new DVRoutingTableEntry(in_entry.getDestination(), iface, new_metric, router.getCurrentTime());
                            table.add(rt_new);
                        }
                    } else if (match.getInterface() == iface && match.getMetric() < INFINITY) {
                        // Incoming iface and entry iface match, existing metric is not infinity
                        // Force update the metric
                        match.setMetric(new_metric);
                        // Reset garbage collector timer if new metric is infinity
                        if (new_metric >= INFINITY) match.setTime(router.getCurrentTime());
                    } else if (new_metric < match.getMetric()) {
                        // Existing entry has greater metric than incoming entry, optimise metric
                        match.setMetric(new_metric);
                        match.setInterface(iface);
                    }
                }
            }
            if (p.getType() == Packet.DATA) {
                // Packet is data type
                int out_iface = getNextHop(p.getDestination());
                if (out_iface > -1) router.send(p, out_iface);
            }
        } else {
            // Interface is down, don't do anything
            assert true;
        }

    }

    public void showRoutes() {
        // Bubble sort table entries (destination ascending)
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
    private int destination;
    private int iface;
    private int metric;
    private int time;

    public DVRoutingTableEntry(int d, int i, int m, int t) {
        destination = d;
        iface = i;
        metric = m;
        time = t;
    }

    public int getDestination() {
        return destination;
    }

    public void setDestination(int d) {
        destination = d;
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
        return "d " + destination + " i " + iface + " m " + metric;
    }
}