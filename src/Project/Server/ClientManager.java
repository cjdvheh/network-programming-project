package Project.Server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

//*본인이 작성함*
// 서버에 접속한 모든 사용자를 관리한다. (로그인 전 사용자 포함, 게임방은 RoomManager가 관리)
public class ClientManager {
    // 쓰기 시 배열 복사본을 만들어 스레드 안전 (읽기는 잠그지 않음)
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    //원자적 int
    private final AtomicInteger sequence = new AtomicInteger(0);

    public void add(ClientHandler client) {
        clients.add(client);
    }

    public void remove(ClientHandler client) {
        clients.remove(client);
    }

    public int count() {
        return clients.size();
    }

    // 로그인 성공 순서대로 번호를 발급한다. (동시에 로그인해도 번호가 겹치지 않음)
    public int nextNumber() {
        return sequence.incrementAndGet();
    }
}
