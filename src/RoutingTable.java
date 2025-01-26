package dockernet;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class RoutingTable {
    private Map<String, RouteInfo> table;

    public RoutingTable() {
        this.table = new ConcurrentHashMap<>();
    }

    // 라우팅 테이블에 경로 추가
    public void addRoutingPath(String destination, int distance, int hops, String nextHop) {
        synchronized (table) { // 동기화를 통해 다중 스레드 환경에서 안전하게 작업
            if (table.containsKey(destination)) {
                int currentDistance = table.get(destination).getDistance();
                if (distance >= currentDistance) {
                    return;
                }
            }
            table.put(destination, new RouteInfo(distance, hops, nextHop));
            System.out.println("Route added/updated: " + destination + " via " + nextHop + " with distance " + distance + " and hops " + hops);
        }
    }

    // 라우팅 테이블 직렬화
    public String serialize(List<String> selfIPAddresses) {
        if (table.isEmpty()) {
            return "ROUTING_TABLE;" + String.join(",", selfIPAddresses);
        }
        
        StringBuilder sb = new StringBuilder("ROUTING_TABLE;");
        for (Map.Entry<String, RouteInfo> entry : table.entrySet()) {
            sb.append(entry.getKey())
              .append(":")
              .append(entry.getValue().getDistance())
              .append(":")
              .append(entry.getValue().getHops())
              .append(",");
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public Set<Entry<String, RouteInfo>> entrySet() {
        return table.entrySet();
    }

    public boolean containsKey(String key) {
        return table.containsKey(key);
    }

    public int getDistance(String destination) {
        return table.get(destination).getDistance();
    }
}