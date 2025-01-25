package dockernet;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Router extends NetworkDevice {
    private Map<String, RouteInfo> routingTable; // 라우팅 테이블
    private static final int BROADCAST_PORT = 5000; // 브로드캐스트 통신 포트
    private final int delay; // 응답 딜레이(ms)
    private final ScheduledExecutorService scheduler; // 주기적인 브로드캐스트 및 업데이트를 위한 스케줄러

    public Router() {
        this(new Random().nextInt(900) + 100); // 100~999ms 랜덤 딜레이
    }

    public Router(int delay) {
        super();
        this.routingTable = new ConcurrentHashMap<>();
        this.delay = delay;
        this.scheduler = Executors.newScheduledThreadPool(2);

        initializeRoutingTable();

        // 초기 브로드캐스트 및 업데이트 시작
        startBroadcastingAndListening();

        // 라우터 학습 및 로그 저장 반복
        startLearningAndLogging();
    }

    // 자신으로 라우팅 테이블을 초기화
    private void initializeRoutingTable() {
        for (String ip : getIPAddresses()) {
            addRoute(ip, 0, 0, ip); // 자신의 IP는 거리 0, 홉 0으로 추가
        }
    }
    
    // 브로드캐스트 및 수신 작업 시작
    private void startBroadcastingAndListening() {
        scheduler.scheduleAtFixedRate(this::sendRoutingTable, 0, 10, TimeUnit.SECONDS); // 10초 간격으로 브로드캐스트
        new Thread(this::listenForBroadcast).start(); // 브로드캐스트 수신
    }

    // 라우팅 테이블을 브로드캐스트
    private void sendRoutingTable() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String tableData = serializeRoutingTable();
            byte[] buffer = tableData.getBytes();

            for (String ip : getIPAddresses()) {
                InetAddress broadcastAddress = getBroadcastAddress(ip);
                if (broadcastAddress != null) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, BROADCAST_PORT);
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending routing table: " + e.getMessage());
        }
    }

    // 라우팅 테이블 수신 및 병합
    private void listenForBroadcast() {
        try (DatagramSocket socket = new DatagramSocket(BROADCAST_PORT)) {
            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    
            while (true) {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                String sourceIP = packet.getAddress().getHostAddress();
    
                // 자신의 IP에서 온 메시지는 무시
                if (getIPAddresses().contains(sourceIP)) {
                    continue;
                }
    
                // 빈 메시지는 기본적으로 무시
                if (message.isEmpty()) {
                    System.err.println("Received an empty message, ignoring.");
                    continue;
                }
    
                // 라우팅 테이블 병합
                mergeRoutingTable(message, sourceIP);
            }
        } catch (Exception e) {
            System.err.println("Error listening for broadcast: " + e.getMessage());
        }
    }    

    // 라우팅 테이블 병합
    private void mergeRoutingTable(String message, String sourceIP) {
        if (!message.startsWith("ROUTING_TABLE;")) {
            System.err.println("Invalid routing table format, ignoring.");
            return;
        }
    
        String tableData = message.substring("ROUTING_TABLE;".length());
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
            int newDistance = distance + this.delay; // 누적 딜레이 반영
            int newHops = hops + 1; // 홉 수 증가
            if (!routingTable.containsKey(destination) || routingTable.get(destination).getDistance() > newDistance) {
                addRoute(destination, newDistance, newHops, sourceIP);
            }
        }
    }    

    // 라우팅 테이블 직렬화
    private String serializeRoutingTable() {
        if (routingTable.isEmpty()) {
            return "ROUTING_TABLE;" + String.join(",", getIPAddresses());
        }
        StringBuilder sb = new StringBuilder("ROUTING_TABLE;");
        for (Map.Entry<String, RouteInfo> entry : routingTable.entrySet()) {
            sb.append(entry.getKey())
              .append(":")
              .append(entry.getValue().getDistance())
              .append(":")
              .append(entry.getValue().getHops())
              .append(",");
        }
        return sb.toString();
    }    

    // 브로드캐스트 주소 계산
    private InetAddress getBroadcastAddress(String ip) {
        try {
            String[] parts = ip.split("\\.");
            return InetAddress.getByName(parts[0] + "." + parts[1] + "." + parts[2] + ".255");
        } catch (Exception e) {
            System.err.println("Error calculating broadcast address for " + ip + ": " + e.getMessage());
            return null;
        }
    }

    // 라우팅 테이블에 경로 추가
    private void addRoute(String destination, int distance, int hops, String nextHop) {
        synchronized (routingTable) { // 동기화를 통해 다중 스레드 환경에서 안전하게 작업
            if (routingTable.containsKey(destination)) {
                int currentDistance = routingTable.get(destination).getDistance();
                if (distance >= currentDistance) {
                    return;
                }
            }
            routingTable.put(destination, new RouteInfo(distance, hops, nextHop));
            System.out.println("Route added/updated: " + destination + " via " + nextHop + " with distance " + distance + " and hops " + hops);
        }
    }

    // 라우팅 테이블 주기적으로 출력
    private void startLearningAndLogging() {
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("\n[Router Update] Current Routing Table:");
            printRoutingTable();
        }, 0, 10, TimeUnit.SECONDS);
    }

    // 라우팅 테이블 출력
    private void printRoutingTable() {
        synchronized (routingTable) {
            if (routingTable.isEmpty()) {
                System.out.println("Routing table is empty.");
                return;
            }

            System.out.println("Destination\tDistance\tHops\tNext Hop");
            for (Map.Entry<String, RouteInfo> entry : routingTable.entrySet()) {
                System.out.println(entry.getKey() + "\t\t" + entry.getValue().getDistance() + "\t\t" + entry.getValue().getHops() + "\t\t" + entry.getValue().getNextHop());
            }
        }
    }

    // 라우팅 정보 클래스
    private static class RouteInfo {
        private final int distance;
        private final int hops;
        private final String nextHop;

        public RouteInfo(int distance, int hops, String nextHop) {
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
}