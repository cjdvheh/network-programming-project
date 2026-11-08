package Project.Server;

import Project.Protocol.MessageProtocol;
import Project.Room.WaitingRoom;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;


// 클라이언트 1명당 1개 생성되는 스레드. (기존 UserService)
// 역할: 소켓으로 메시지를 받고(readUTF), 보내고(writeUTF), 알맞은 객체에 넘긴다.
// 게임 규칙은 직접 처리하지 않는다.
public class ClientHandler extends Thread {
    private final Socket socket;
    private final DataInputStream dis;
    private final DataOutputStream dos;
    private final MainServer server;
    private final ClientManager clientManager;
    private final WaitingRoom waitingRoom;

    private volatile String displayName = null;  // 로그인 전에는 null
    private volatile boolean closed = false;
    private final Object closeLock = new Object();

    // 주의: 생성자에서는 스트림만 만든다. readUTF()는 run()에서 한다.
    // (기존 코드는 생성자에서 readUTF()를 해서 접속만 하고 말이 없는 클라이언트가
    //  있으면 accept 스레드 전체가 멈추는 문제가 있었다.)
    public ClientHandler(Socket socket, MainServer server,
                         ClientManager clientManager, WaitingRoom waitingRoom) throws IOException {
        this.socket = socket;
        this.server = server;
        this.clientManager = clientManager;
        this.waitingRoom = waitingRoom;
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
    }

    public String getDisplayName() {
        return displayName == null ? "(로그인 전)" : displayName;
    }

    @Override
    public void run() {
        try {
            while (!closed) {
                String line = dis.readUTF().trim();
                handle(line);
            }
        } catch (IOException e) {
            server.appendLog("연결 종료 사유: " + e);   // 추가
        } catch (Exception e) {
            server.appendLog("처리 중 예외: " + e);      // 추가 (IOException 외 예외)
            e.printStackTrace();
        } finally {
            close();
        }
    }

    // 받은 메시지를 파싱해서 처리한다. (기존 switch(args[1]) 부분)
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
        if (nickname.isEmpty() || nickname.length() > 10
                || nickname.contains("|") || nickname.contains(",")) {
            send(MessageProtocol.build(MessageProtocol.LOGIN_FAIL,
                    "닉네임은 1~10자이며 | 와 , 는 사용할 수 없습니다."));
            return;
        }

        // 입장 순서 + 닉네임 => 표시 이름. 번호가 항상 다르므로 중복 검사가 필요 없다.
        displayName = clientManager.nextNumber() + "_" + nickname;
        send(MessageProtocol.build(MessageProtocol.LOGIN_OK, displayName));
        server.appendLog("로그인: " + displayName + " (현재 접속자 " + clientManager.count() + "명)");

        waitingRoom.enter(this);   // 로그인하면 대기실로 입장
    }

    // 이 클라이언트에게 메시지 전송 (기존 WriteOne)
    // 여러 스레드가 동시에 같은 클라이언트에게 보낼 수 있으므로 dos를 잠근다.
    public void send(String msg) {
        try {
            synchronized (dos) {
                dos.writeUTF(msg);
            }
        } catch (IOException e) {
            server.appendLog("전송 실패: " + getDisplayName());
            close();   // 잠금 밖에서 호출 (교착 상태 방지)
        }
    }

    // 연결 종료 및 정리. 여러 번 호출되어도 한 번만 실행된다. (기존 logout)
    public void close() {
        synchronized (closeLock) {
            if (closed) return;
            closed = true;
        }
        try {
            socket.close();   // readUTF()에서 대기 중인 run()도 깨어난다.
        } catch (IOException ignored) {
        }
        waitingRoom.leave(this);
        clientManager.remove(this);
        server.appendLog("퇴장: " + getDisplayName() + " (현재 접속자 " + clientManager.count() + "명)");
    }
}