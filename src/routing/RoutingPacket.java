package routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoutingPacket {
    private static final String ROUTING_TABLE_PREFIX = "ROUTING_TABLE;";

    private final List<RoutingInfo> routeInfos;

    public RoutingPacket(String message) {
        this.routeInfos = new ArrayList<RoutingInfo>();

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

            routeInfos.add(new RoutingInfo(distance, hops, destination));
        }
    }
    
    public List<RoutingInfo> getRouteInfos() {
        return routeInfos;
    }

    // 라우팅 테이블을 패킷으로 변환
    public static String create(RoutingTable table, List<String> selfIPAddresses) {
        if (table.isEmpty()) {
            for (String selfIP : selfIPAddresses) {
                table.addRoutingPath(selfIP, 0, 0, selfIP);
            }
        }
        
        StringBuilder sb = new StringBuilder(ROUTING_TABLE_PREFIX);
        
        for (Map.Entry<String, RoutingInfo> entry : table.entrySet()) {
            sb.append(entry.getKey())
              .append(":")
              .append(entry.getValue().getDistance())
              .append(":")
              .append(entry.getValue().getHops())
              .append(",");
        }
        return sb.toString();
    }

    // 정상적인 라우팅 패킷인지 확인
    public static boolean isRoutingPacket(String message) {
        return message.startsWith(ROUTING_TABLE_PREFIX);
    }
}
