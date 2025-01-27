package device;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import routing.RoutingInfo;
import routing.RoutingPacket;
import routing.RoutingTable;

public class Router extends NetworkDevice {
    private static final int BROADCAST_INTERVAL = 5; // 라우팅 테이블 브로드캐스트 간격

    private final RoutingTable routingTable; // 라우팅 테이블
    private final ScheduledExecutorService scheduler; // 주기적인 브로드캐스트 및 업데이트를 위한 스케줄러
    private final int delay; // 응답 딜레이(ms)

    public Router() {
        this(new Random().nextInt(900) + 100); // 100~999ms 랜덤 딜레이
    }

    public Router(int delay) {
        super();
        this.routingTable = new RoutingTable();
        this.delay = delay;
        this.scheduler = Executors.newScheduledThreadPool(3); // 스케줄러 스레드풀 크기 증가
    }

    @Override
    public void run() {
        initializeRoutingTable();
        startBroadcastingAndListening();
        startPacketListening();
        // startLearningAndLogging();
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
            String tableData = RoutingPacket.create(routingTable, getIPAddresses());
            byte[] buffer = tableData.getBytes();

            for (String ip : getIPAddresses()) {
                InetAddress broadcastAddress = getBroadcastAddress(ip);
                if (broadcastAddress != null) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, ROUTING_BROADCAST_PORT);
                    socket.send(packet);
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending routing table: " + e.getMessage());
        }
    }

    // 라우팅 테이블 수신 및 병합
    private void listenForBroadcast() {
        try (DatagramSocket socket = new DatagramSocket(ROUTING_BROADCAST_PORT)) {
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
                routingTable.mergeRoutingTable(message, sourceIP, delay);
            }
        } catch (Exception e) {
            System.err.println("Error listening for broadcast: " + e.getMessage());
        }
    }

    // 패킷 수신 및 처리
    private void startPacketListening() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PACKET_RECEIVE_PORT)) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    String sourceIP = packet.getAddress().getHostAddress();

                    System.out.println("Received packet: " + message + " from " + sourceIP);

                    // 패킷 해석 및 다음 홉으로 전달
                    String destinationIP = extractDestinationIP(message);
                    RoutingInfo route = routingTable.get(destinationIP);

                    if (route != null) {
                        forwardPacket(message, route.getNextHop());
                    } else {
                        System.err.println("No route to destination: " + destinationIP);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error receiving packet: " + e.getMessage());
            }
        }).start();
    }

    // 패킷에서 목적지 IP 추출 (단순 예제 구현)
    private String extractDestinationIP(String queryString) {
        try {
            // 쿼리 문자열 분리
            String[] pairs = queryString.split("&");

            for (String pair : pairs) {
                String[] keyValue = pair.split("=");

                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                    
                    // "destination" 키 확인 후 반환
                    if (key.equals("destination")) {
                        return value;
                    }
                }
            }
            System.err.println("Invalid query string: 'destination' field is missing.");
            return null;
        } catch (Exception e) {
            System.err.println("Error parsing query string: " + e.getMessage());
            return null;
        }
    }

    // 패킷 전달
    private void forwardPacket(String packet, String nextHop) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer = packet.getBytes();
            InetAddress nextHopAddress = InetAddress.getByName(nextHop);
            DatagramPacket forwardPacket = new DatagramPacket(buffer, buffer.length, nextHopAddress, PACKET_RECEIVE_PORT);

            socket.send(forwardPacket);

            System.out.println("Forwarded packet: " + packet + " to " + nextHop);
        } catch (Exception e) {
            System.err.println("Error forwarding packet: " + e.getMessage());
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
            
                for (Map.Entry<String, RoutingInfo> entry : routingTable.entrySet()) {
                    System.out.printf("%-20s %-10d %-10d %-20s\n", 
                        entry.getKey(), entry.getValue().getDistance(), entry.getValue().getHops(), entry.getValue().getNextHop());
                }
            }
        }, 0, BROADCAST_INTERVAL, TimeUnit.SECONDS);
    }
}
