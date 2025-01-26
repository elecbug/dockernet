import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

abstract class NetworkDevice implements IRunnerable {
    protected static final int HTTP_PORT = 8080; // HTTP 서버 포트
    protected static final int ROUTING_BROADCAST_PORT = 5000; // 라우터 브로드캐스트 수신 포트
    protected static final int PACKET_RECEIVE_PORT = 6000; // 라우터가 듣는 포트

    private List<String> ipAddresses; // 실제 IP 주소 목록

    public NetworkDevice() {
        this.ipAddresses = new ArrayList<>();
        fetchIPAddresses();
    }

    // 네트워크 인터페이스에서 IP 주소 가져오기
    private void fetchIPAddresses() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // 활성화된 네트워크 인터페이스만 처리
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                // 네트워크 인터페이스에서 IP 주소 추출
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // IPv4 주소만 처리
                    if (address instanceof Inet4Address) {
                        ipAddresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching IP addresses: " + e.getMessage());
        }
    }

    // 현재 IP 주소 목록 반환
    public List<String> getIPAddresses() {
        return this.ipAddresses;
    }
}
