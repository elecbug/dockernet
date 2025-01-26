package dockernet;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class Router extends NetworkDevice {
    private static final int BROADCAST_PORT = 5000; // 브로드캐스트 통신 포트
    private static final int BROADCAST_INTERVAL = 5; // 라우팅 테이블 브로드캐스트 간격

    private RoutingTable routingTable; // 라우팅 테이블
    private final ScheduledExecutorService scheduler; // 주기적인 브로드캐스트 및 업데이트를 위한 스케줄러
    private final int delay; // 응답 딜레이(ms)

    public Router() {
        this(new Random().nextInt(900) + 100); // 100~999ms 랜덤 딜레이
    }

    public Router(int delay) {
        super();
        this.routingTable = new RoutingTable();
        this.delay = delay;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    @Override
    public void run() {
        initializeRoutingTable();
        startBroadcastingAndListening();
        startLearningAndLogging();
    }

    // 자신으로 라우팅 테이블을 초기화
    private void initializeRoutingTable() {
        for (String ip : getIPAddresses()) {
            routingTable.addRoutingPath(ip, 0, 0, ip); // 자신의 IP는 거리 0, 홉 0으로 추가
        }
    }
    
    // 브로드캐스트 및 수신 작업 시작
    private void startBroadcastingAndListening() {
        scheduler.scheduleAtFixedRate(this::sendRoutingTable, 0, BROADCAST_INTERVAL, TimeUnit.SECONDS); // 10초 간격으로 브로드캐스트
        new Thread(this::listenForBroadcast).start(); // 브로드캐스트 수신
    }

    // 라우팅 테이블을 브로드캐스트
    private void sendRoutingTable() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            String tableData = routingTable.serialize(getIPAddresses());
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
            byte[] buffer = new byte[10240];
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
            if (!routingTable.containsKey(destination) || routingTable.getDistance(destination) > newDistance) {
                routingTable.addRoutingPath(destination, newDistance, newHops, sourceIP);
            }
        }
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

    // 라우팅 테이블 주기적으로 출력
    private void startLearningAndLogging() {
        scheduler.scheduleAtFixedRate(() -> {
            System.out.println("\n[Router Update] Current Routing Table:");
            synchronized (routingTable) {
                if (routingTable.isEmpty()) {
                    System.out.println("Routing table is empty.");
                    return;
                }
    
                System.out.printf("%-20s %-10s %-10s %-20s\n", "Destination", "Distance", "Hops", "Next Hop");
                for (Map.Entry<String, RouteInfo> entry : routingTable.entrySet()) {
                    System.out.printf("%-20s %-10d %-10d %-20s\n", 
                        entry.getKey(), entry.getValue().getDistance(), entry.getValue().getHops(), entry.getValue().getNextHop());
                }
            }
        }, 0, 10, TimeUnit.SECONDS);
    }
}