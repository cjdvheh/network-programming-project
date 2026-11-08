package Project.User;

import Project.Protocol.MessageProtocol;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.function.Consumer;

//클라의 통신만 담당
//모든 화면이 해당 객체를 공유
public class Client {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private volatile Consumer<String> listener;

    public void connect(String ip, int port) throws IOException {
        socket = new Socket(ip, port);
        dos = new DataOutputStream(socket.getOutputStream());
        dis = new DataInputStream(socket.getInputStream());
        Thread t = new Thread(this::listen);
        t.setDaemon(true);
        t.start();
    }
    // 지금 메시지를 받을 화면을 지정한다. 화면이 바뀌면 다시 지정한다.
    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    public void send(String msg) {
        try {
            synchronized (dos) {
                dos.writeUTF(msg);
            }
        } catch (IOException e) {
            close();
        }
    }


    private void listen() {
        try {
            while (true) {
                deliver(dis.readUTF());
            }
        } catch (IOException e) {
            deliver(MessageProtocol.DISCONNECTED);
        }
    }

    // Swing 컴포넌트는 EDT에서만 갱신해야 하므로 invokeLater로 전달한다.
    // listener는 "실행 시점"에 읽는다. 그래서 LOGIN_OK 처리 중 화면을 바꿔도
    // 바로 뒤따라온 메시지가 새 화면으로 전달된다.
    private void deliver(String msg) {
        SwingUtilities.invokeLater(() -> {
            Consumer<String> l = listener;
            if (l != null) l.accept(msg);
        });
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}
