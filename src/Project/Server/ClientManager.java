package Project.Server;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

//*본인이 작성함*
// 서버에 접속한 모든 사용자를 관리한다. (로그인 전 사용자 포함, 게임방은 RoomManager가 관리)
public class ClientManager {
    // 쓰기 시 배열 복사본을 만들어 스레드 안전 (읽기는 잠그지 않음)
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // 로그인한 사용자의 닉네임 (중복 검사용)
    private final Set<String> nameSet = new HashSet<>();

    public void add(ClientHandler client) {
        clients.add(client);
    }

    public synchronized void remove(ClientHandler client) {
        // 로그인한 사용자만 닉네임을 지운다. (로그인 전 접속이 남의 닉네임을 지우지 않도록)
        if (client.isLoggedIn()) {
            nameSet.remove(client.getDisplayName());
        }
        clients.remove(client);
    }

    public int count() {
        return clients.size();
    }

    // 닉네임이 중복인지 검사하고, 문제없으면 닉네임을 등록한다.
    public synchronized boolean registerName(String nickname) {
        return nameSet.add(nickname);   // 이미 있으면 false
    }
}
