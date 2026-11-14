package Project.Room;

import Project.Server.ClientHandler;
import Project.Protocol.MessageProtocol;

import java.util.ArrayList;
import java.util.List;

// 대기실에 있는 사용자만 관리한다.
// 게임방에 들어간 사용자는 여기서 빠지고, 방에서 나오면 다시 들어온다.
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

    // 대기실 사용자 전원에게 메시지 전송 (방 목록 갱신 등에 사용)
    public synchronized void broadcast(String msg) {
        for (ClientHandler u : new ArrayList<>(users)) {
            u.send(msg);
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
        // send()가 실패해도 지금은 소켓만 닫고 leave()는 해당 사용자 스레드가 나중에 부른다.
        // 복사본 순회는 send() 동작이 바뀌어도 안전하도록 남겨 둔 안전장치다.
        for (ClientHandler u : new ArrayList<>(users)) {
            u.send(msg);
        }
    }
}