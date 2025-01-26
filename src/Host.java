import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Host extends NetworkDevice {
    private RoutingTable routingTable;

    public Host() {
        super();
        routingTable = new RoutingTable();
    }

    @Override
    public void run() {
        try {
            startHttpServer();
            listenForRoutingBroadcasts();
        } catch (IOException e) {
            System.err.println("Failed to start services: " + e.getMessage());
        }
    }

    // HTTP 서버 시작
    private void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        server.createContext("/send-packet", new SendPacketHandler());
        server.setExecutor(Executors.newFixedThreadPool(4)); // 스레드 풀 설정
        server.start();
        System.out.println("HTTP server started on port " + HTTP_PORT);
    }

    // 브로드캐스트 수신 및 응답
    private void listenForRoutingBroadcasts() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(ROUTING_BROADCAST_PORT)) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    String sourceIP = packet.getAddress().getHostAddress();

                    // System.out.println("Received routing broadcast from " + sourceIP + ": " + message);

                    // 메시지가 "ROUTING_TABLE;"인지 확인
                    if (message.startsWith(RoutingTable.ROUTING_TABLE_PREFIX)) {
                        respondToRoutingRequest(sourceIP);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error listening for broadcasts: " + e.getMessage());
            }
        }).start();
    }

    // 라우팅 요청에 응답
    private void respondToRoutingRequest(String sourceIP) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String response = routingTable.serialize(getIPAddresses());
            byte[] buffer = response.getBytes();
            InetAddress address = InetAddress.getByName(sourceIP);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, ROUTING_BROADCAST_PORT);
            socket.send(packet);
            // System.out.println("Sent routing response to " + sourceIP);
        } catch (Exception e) {
            System.err.println("Error sending routing response: " + e.getMessage());
        }
    }

    // 패킷 전송 핸들러
    private class SendPacketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            String requestData = new String(requestBody);
            System.out.println("Received packet data: " + requestData);

            // 패킷 데이터 전송
            boolean success = sendPacket(requestData);

            if (success) {
                sendResponse(exchange, 200, "Packet sent successfully");
            } else {
                sendResponse(exchange, 500, "Failed to send packet");
            }
        }
    }

    // 브로드캐스트로 패킷 전송
    private boolean sendPacket(String data) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);

            for (String ip : getIPAddresses()) {
                InetAddress broadcastAddress = getBroadcastAddress(ip);
                if (broadcastAddress != null) {
                    byte[] buffer = data.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, PACKET_RECEIVE_PORT);
                    socket.send(packet);
                    System.out.println("Packet broadcasted to " + broadcastAddress.getHostAddress());
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error sending packet: " + e.getMessage());
            return false;
        }
    }

    // HTTP 응답 전송
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
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
}
