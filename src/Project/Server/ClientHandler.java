package Project.Server;

import Project.Game.GameRule;
import Project.Protocol.MessageProtocol;
import Project.Room.GameRoom;
import Project.Room.RoomManager;
import Project.Room.WaitingRoom;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

//*본인이 작성함*
// 클라이언트 1명당 1개 생성되는 스레드.
// 역할: 소켓으로 메시지를 받고(readUTF), 보내고(writeUTF), 알맞은 객체에 넘긴다.
// 게임 규칙은 직접 처리하지 않는다.
public class ClientHandler extends Thread {
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final MainServer server;
    private final ClientManager clientManager;
    private final WaitingRoom waitingRoom;
    private final RoomManager roomManager;

    private volatile String displayName = null;  // 로그인 전에는 null
    private volatile GameRoom currentRoom = null; // 대기실에 있으면 null
    private volatile boolean closed = false;
    private volatile boolean broken = false;   // 전송 실패로 연결이 끊긴 상태
    private final Object closeLock = new Object();

    // 주의: 생성자에서는 스트림만 만든다. readUTF()는 run()에서 한다.
    // 생성자는 accept 스레드에서 실행되므로, 여기서 readUTF()를 하면 메시지를 보내지 않는
    // 클라이언트 하나 때문에 accept 스레드가 멈춰 다른 사용자가 접속할 수 없다.
    public ClientHandler(Socket socket, MainServer server, ClientManager clientManager,
                         WaitingRoom waitingRoom, RoomManager roomManager) throws IOException {
        this.socket = socket;
        this.server = server;
        this.clientManager = clientManager;
        this.waitingRoom = waitingRoom;
        this.roomManager = roomManager;
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
    }

    public String getDisplayName() {
        return displayName == null ? "(로그인 전)" : displayName;
    }

    public boolean isLoggedIn() {
        return displayName != null;
    }

    // 현재 들어가 있는 게임방 (RoomManager가 잠금 안에서 바꾼다)
    public GameRoom getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(GameRoom room) {
        this.currentRoom = room;
    }

    @Override
    public void run() {
        try {
            while (!closed) {
                String line = dis.readUTF().trim();
                handle(line);
            }
        } catch (IOException e) {
            server.appendLog("연결 종료 사유: " + e);
        } catch (Exception e) {
            server.appendLog("처리 중 예외: " + e);      // IOException 외 예외
            e.printStackTrace();
        } finally {
            close();
        }
    }

    // 받은 메시지를 파싱해서 처리한다.
    private void handle(String line) {
        String[] msg = MessageProtocol.parse(line);
        String cmd = msg[0];
        String body = msg[1];

        // 로그인 전에는 LOGIN 외의 명령을 받지 않는다.
        if (displayName == null && !cmd.equals(MessageProtocol.LOGIN)) {
            send(MessageProtocol.build(MessageProtocol.LOGIN_FAIL, "먼저 로그인해야 합니다."));
            return;
        }

        switch (cmd) {
            case MessageProtocol.LOGIN:
                handleLogin(body);
                break;
            case MessageProtocol.LOGOUT:
                close();
                break;
            case MessageProtocol.ROOM_CREATE:
                handleRoomCreate(body);
                break;
            case MessageProtocol.ROOM_JOIN:
                handleRoomJoin(body);
                break;
            case MessageProtocol.ROOM_LEAVE:
                roomManager.leaveRoom(this, true);   // 게임방 -> 대기실
                break;
            case MessageProtocol.ROOM_CHAT:
                handleRoomChat(body);
                break;
            case MessageProtocol.GAME_START:
                roomManager.startGame(this);
                break;
            case MessageProtocol.ACTION:        // 밤 능력 (마피아 지목 / 의사 치료 / 경찰 조사)
            case MessageProtocol.VOTE:          // 투표
            case MessageProtocol.EXTEND_TIME:   // 낮 토론 시간 연장
                roomManager.gameAction(this, cmd, body.trim());
                break;
            default:
                server.appendLog("알 수 없는 명령: " + line);
        }
    }

    private void handleLogin(String nickname) {
        if (displayName != null) {
            send(MessageProtocol.build(MessageProtocol.LOGIN_FAIL, "이미 로그인했습니다."));
            return;
        }
        nickname = nickname.trim();
        if (nickname.isEmpty() || nickname.length() > 10 || MessageProtocol.hasReservedChar(nickname)
                || nickname.matches(".*[()\\[\\]].*")) {   // 괄호는 "(방장)", "[마피아]" 같은 화면 표시와 헷갈림
            send(MessageProtocol.build(MessageProtocol.LOGIN_FAIL,
                    "닉네임은 1~10자이며 | , ; ( ) [ ] 는 사용할 수 없습니다."));
            return;
        }

        if (!clientManager.registerName(nickname)) {
            send(MessageProtocol.build(MessageProtocol.LOGIN_FAIL,
                    "중복된 닉네임입니다. 다른 닉네임을 선택하세요."));
            return;
        }

        // 닉네임 중복 검사를 통과했으므로 닉네임을 그대로 표시 이름으로 쓴다.
        displayName = nickname;
        send(MessageProtocol.build(MessageProtocol.LOGIN_OK, displayName));
        server.appendLog("로그인: " + displayName + " (현재 접속자 " + clientManager.count() + "명)");

        waitingRoom.enter(this);   // 로그인하면 대기실로 입장
        roomManager.sendRoomList(this);   // 현재 방 목록도 보내준다
    }

    // ROOM_CREATE|방제목,비밀번호
    private void handleRoomCreate(String body) {
        String[] f = body.split(MessageProtocol.FIELD_SEP, -1);
        String title = f[0].trim();
        String password = f.length > 1 ? f[1].trim() : "";
        if (f.length > 2 || title.isEmpty() || title.length() > GameRule.MAX_TITLE_LENGTH
                || password.length() > GameRule.MAX_PASSWORD_LENGTH
                || MessageProtocol.hasReservedChar(title) || MessageProtocol.hasReservedChar(password)) {
            send(MessageProtocol.build(MessageProtocol.ROOM_FAIL,
                    "방 제목은 1~" + GameRule.MAX_TITLE_LENGTH + "자, 비밀번호는 0~" + GameRule.MAX_PASSWORD_LENGTH
                            + "자이며 | , ; 는 사용할 수 없습니다."));
            return;
        }
        roomManager.createRoom(this, title, password);
    }

    // ROOM_JOIN|방번호,비밀번호
    private void handleRoomJoin(String body) {
        String[] f = body.split(MessageProtocol.FIELD_SEP, -1);
        int roomId;
        try {
            roomId = Integer.parseInt(f[0].trim());
        } catch (NumberFormatException e) {
            send(MessageProtocol.build(MessageProtocol.ROOM_FAIL, "잘못된 방 번호입니다."));
            return;
        }
        String password = f.length > 1 ? f[1].trim() : "";
        roomManager.joinRoom(this, roomId, password);
    }

    // ROOM_CHAT|내용
    private void handleRoomChat(String body) {
        String text = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (text.isEmpty()) return;
        if (text.length() > GameRule.MAX_CHAT_LENGTH) {
            text = text.substring(0, GameRule.MAX_CHAT_LENGTH);
        }
        roomManager.chat(this, text);
    }

    // 이 클라이언트에게 메시지 전송
    // 여러 스레드가 동시에 같은 클라이언트에게 보낼 수 있으므로 dos를 잠근다.
    public void send(String msg) {
        if(broken || closed) return;
        try {
            synchronized (dos) {
                dos.writeUTF(msg);
            }
        } catch (IOException e) {
            broken = true;
            server.appendLog("전송 실패: " + getDisplayName());
            // 여기서 close()를 바로 부르면, 대기실/방 잠금을 잡은 스레드가 다른 잠금을
            // 기다리게 되어 교착 상태가 생길 수 있다. 소켓만 닫으면 이 클라이언트의
            // run()이 readUTF() 예외로 깨어나 finally의 close()에서 정리한다.
            closeSocket();
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // 연결 종료 및 정리. 여러 번 호출되어도 한 번만 실행된다.
    public void close() {
        synchronized (closeLock) {
            if (closed) return;
            closed = true;
        }
        closeSocket();   // readUTF()에서 대기 중인 run()도 깨어난다.
        roomManager.leaveRoom(this, false);   // 게임방에 있었다면 방에서 나간다
        waitingRoom.leave(this);
        clientManager.remove(this);
        server.appendLog("퇴장: " + getDisplayName() + " (현재 접속자 " + clientManager.count() + "명)");
    }
}