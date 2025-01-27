package routing;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingTable {
    private final Map<String, RoutingInfo> table;

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
            table.put(destination, new RoutingInfo(distance, hops, nextHop));

            System.out.println("Route added/updated: " + destination + " via " + nextHop 
                + " with distance " + distance + " and hops " + hops);
        }
    }

    // 라우팅 테이블 병합
    public void mergeRoutingTable(String message, String sourceIP, int delay) {
        RoutingPacket packet = new RoutingPacket(message);

        for (RoutingInfo info : packet.getRouteInfos()) {
            // 기존 정보와 비교하여 더 짧은 경로로 업데이트
            int newDistance = info.getDistance() + delay; // 누적 딜레이 반영
            int newHops = info.getHops() + 1; // 홉 수 증가
            
            if (!containsKey(info.getNextHop()) || getDistance(info.getNextHop()) > newDistance) {
                addRoutingPath(info.getNextHop(), newDistance, newHops, sourceIP);
            }
        }
    }    

    public boolean isEmpty() {
        return table.isEmpty();
    }

    public Set<Entry<String, RoutingInfo>> entrySet() {
        return table.entrySet();
    }

    public boolean containsKey(String key) {
        return table.containsKey(key);
    }

    public int getDistance(String destination) {
        return table.get(destination).getDistance();
    }

    public RoutingInfo get(String destinationIP) {
        return table.get(destinationIP);
    }
}