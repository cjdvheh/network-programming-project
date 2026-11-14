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
        //대기실의 모든 사람에게 전송
        broadcastAll(enterUser(user));

        //이후 들어온 사람에게 현재 목록을 전송
        users.add(user);
        sendUserList(user);

    }

    public synchronized void leave(ClientHandler user) {
        if (users.remove(user)) {   // 대기실에 있던 사람일 때만 갱신
            broadcastAll(leaveUser(user));
        }
    }

    // 대기실 사용자 전원에게 메시지 전송 (방 목록 갱신 등에 사용)
    public synchronized void broadcastAll(String msg) {
        for (ClientHandler u : new ArrayList<>(users)) {
            u.send(msg);
        }
    }


    private String enterUser(ClientHandler client)
    {
        String name = client.getDisplayName();
        String msg = MessageProtocol.build(MessageProtocol.USER_JOIN,name);
        return msg;
    }

    private String leaveUser(ClientHandler client)
    {
        String name = client.getDisplayName();
        String msg = MessageProtocol.build(MessageProtocol.USER_LEAVE,name);
        return msg;
    }

    private String currentAllUserList()
    {
        StringBuilder names = new StringBuilder();
        for (ClientHandler u : users) {
            if (!names.isEmpty()) names.append(",");
            names.append(u.getDisplayName());
        }
        return MessageProtocol.build(MessageProtocol.WAITING_USERS, names.toString());
    }

    // 대기실 사용자 한명에게 현재 모든 접속자 목록을 보낸다.
    private void sendUserList(ClientHandler client) {
        client.send(currentAllUserList());
    }
}