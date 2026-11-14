package Project.Ui;

import Project.Protocol.MessageProtocol;
import Project.User.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.function.Consumer;

public class LoginFrame extends JFrame {
    private final JTextField username = new JTextField();
    private final JTextField ip = new JTextField("127.0.0.1");
    private final JTextField port = new JTextField("30000");
    private final JButton btnConnect = new JButton("Connect");
    private Client client;
    private Consumer<WaitingRoomFrame> onWaitingRoom;   // 개발용: 대기실이 열리면 실행할 동작

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 실패하면 기본 모양(Metal)으로 그냥 실행된다
        }
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    public LoginFrame() {
        setTitle("Mafia - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(280, 220);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(4, 2, 5, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(new JLabel("닉네임", SwingConstants.CENTER));
        panel.add(username);
        panel.add(new JLabel("IP Address", SwingConstants.CENTER));
        panel.add(ip);
        panel.add(new JLabel("Port", SwingConstants.CENTER));
        panel.add(port);
        panel.add(new JLabel());
        panel.add(btnConnect);
        setContentPane(panel);

        btnConnect.addActionListener(e -> connect());
        username.addActionListener(e -> connect());
    }

    // 개발용: 닉네임을 채우고 바로 접속한다. (로그인에 성공하면 onMessage가 대기실을 연다)
    public void autoLogin(String nickname) {
        autoLogin(nickname, null);
    }

    // 개발용: 자동 로그인 후 대기실이 열리면 onWaitingRoom을 실행한다. (예: 방 생성 / 방 입장)
    public void autoLogin(String nickname, Consumer<WaitingRoomFrame> onWaitingRoom) {
        this.onWaitingRoom = onWaitingRoom;
        username.setText(nickname);
        connect();
    }

    public static void askReconnect(JFrame current, String nickname)
    {
        int answer = JOptionPane.showConfirmDialog(current,
                "서버와 연결이 끊어졌습니다. 다시 접속할까요?" ,
                "연결 끊김", JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION)
        {
            LoginFrame login = new LoginFrame();
            login.setNickname(nickname);
            login.setLocationRelativeTo(current);   // 원래 창 자리에 띄우기
            login.setVisible(true);
            current.dispose();                      // 끊긴 화면 닫기
        }
        else{
            System.exit(0);
        }
    }
    // 닉네임 칸을 미리 채운다. (다시 접속할 때 사용)
    public void setNickname(String nickname) {
        username.setText(nickname);
    }
    //접속버튼 & 이름입력칸 이벤트 함수
    private void connect() {
        String nickname = username.getText().trim();
        if (nickname.isEmpty()) {
            JOptionPane.showMessageDialog(this, "닉네임을 입력하세요.");
            return;
        }
        btnConnect.setEnabled(false);

        client = new Client();
        try {
            client.connect(ip.getText().trim(), Integer.parseInt(port.getText().trim()));
        } catch (NumberFormatException | IOException ex) {
            JOptionPane.showMessageDialog(this, "서버 접속 실패: " + ex.getMessage());
            btnConnect.setEnabled(true);
            return;
        }
        client.setListener(this::onMessage);
        client.send(MessageProtocol.build(MessageProtocol.LOGIN, nickname));
    }

    // 서버가 보낸 메시지 처리 (EDT에서 호출됨)
    private void onMessage(String msg) {
        String[] m = MessageProtocol.parse(msg);
        switch (m[0]) {
            case MessageProtocol.LOGIN_OK:
                WaitingRoomFrame waiting = new WaitingRoomFrame(client, m[1]);   // m[1] = 내 표시 이름
                waiting.setVisible(true);
                if (onWaitingRoom != null) onWaitingRoom.accept(waiting);
                dispose();
                break;
            case MessageProtocol.LOGIN_FAIL:
                client.setListener(null);
                client.close();
                JOptionPane.showMessageDialog(this, "로그인 실패: " + m[1]);
                btnConnect.setEnabled(true);
                break;
            case MessageProtocol.DISCONNECTED:
                JOptionPane.showMessageDialog(this, "서버와 연결이 끊어졌습니다.");
                btnConnect.setEnabled(true);
                break;
        }
    }
}
