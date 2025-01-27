package routing;

public class RoutingInfo {
    private final int distance;
    private final int hops;
    private final String nextHop;

    public RoutingInfo(int distance, int hops, String nextHop) {
        this.distance = distance;
        this.hops = hops;
        this.nextHop = nextHop;
    }

    public int getDistance() {
        return distance;
    }

    public int getHops() {
        return hops;
    }

    public String getNextHop() {
        return nextHop;
    }
}
