package Project.Server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

//서버에 접속한 모든 사용자를 관리 , 모든 방 포함
//
public class ClientManager {
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
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
