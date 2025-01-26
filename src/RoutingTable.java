import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    public static final String ROUTING_TABLE_PREFIX = "ROUTING_TABLE;";

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
            for (String selfIP : selfIPAddresses) {
                addRoutingPath(selfIP, 0, 0, selfIP);
            }
        }
        
        StringBuilder sb = new StringBuilder(ROUTING_TABLE_PREFIX);
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

    // 라우팅 테이블 병합
    public void mergeRoutingTable(String message, String sourceIP, int delay) {
        if (!message.startsWith(ROUTING_TABLE_PREFIX)) {
            System.err.println("Invalid routing table format, ignoring.");
            return;
        }
    
        String tableData = message.substring(ROUTING_TABLE_PREFIX.length());
        if (tableData.isEmpty()) {
            System.err.println("Received an empty routing table, ignoring.");
            return;
        }
    
        String[] entries = tableData.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length != 3) {
                System.err.println("Malformed routing table entry: " + entry);
                continue;
            }
    
            String destination = parts[0];
            int distance;
            int hops;
            try {
                distance = Integer.parseInt(parts[1]);
                hops = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid distance or hops value for entry: " + entry);
                continue;
            }
    
            // 기존 정보와 비교하여 더 짧은 경로로 업데이트
            int newDistance = distance + delay; // 누적 딜레이 반영
            int newHops = hops + 1; // 홉 수 증가
            if (!containsKey(destination) || getDistance(destination) > newDistance) {
                addRoutingPath(destination, newDistance, newHops, sourceIP);
            }
        }
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

    public RouteInfo get(String destinationIP) {
        return table.get(destinationIP);
    }
}