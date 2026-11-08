package Project.Room;

import Project.Server.ClientHandler;
import Project.Protocol.MessageProtocol;

import java.util.ArrayList;
import java.util.List;

// 대기실에 있는 사용자만 관리한다.
// 게임방에 들어간 사용자는 여기서 빠지고, 방에서 나오면 다시 들어온다. (다음 단계)
public class WaitingRoom {
    private final List<ClientHandler> users = new ArrayList<>();

    // 입장/퇴장/목록 전송을 synchronized로 묶어, 여러 스레드가 동시에 호출해도
    // 목록이 뒤섞여 전송되지 않게 한다.
    public synchronized void enter(ClientHandler user) {
        users.add(user);
        broadcastUserList();
    }

    public synchronized void leave(ClientHandler user) {
        if (users.remove(user)) {   // 대기실에 있던 사람일 때만 갱신
            broadcastUserList();
        }
    }

    // 대기실 사용자 전원에게 현재 접속자 목록을 보낸다.
    private void broadcastUserList() {
        StringBuilder names = new StringBuilder();
        for (ClientHandler u : users) {
            if (names.length() > 0) names.append(",");
            names.append(u.getDisplayName());
        }
        String msg = MessageProtocol.build(MessageProtocol.WAITING_USERS, names.toString());
        // 순회 중 send 실패로 leave()가 호출될 수 있으므로 복사본으로 순회한다.
        for (ClientHandler u : new ArrayList<>(users)) {
            u.send(msg);
        }
    }
}