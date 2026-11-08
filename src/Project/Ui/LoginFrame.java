package Project.Ui;

import Project.Protocol.MessageProtocol;
import Project.User.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;

public class LoginFrame extends JFrame {
    private final JTextField username = new JTextField();
    private final JTextField ip = new JTextField("127.0.0.1");
    private final JTextField port = new JTextField("30000");
    private final JButton btnConnect = new JButton("Connect");
    private Client client;

    public static void main(String[] args) {
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

    // 개발용: 닉네임을 채우고 바로 접속한다. (로그인 성공 시 기존 코드가 대기실을 연다)
    public void autoLogin(String nickname) {
        username.setText(nickname);
        connect();
    }

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
                new WaitingRoomFrame(client, m[1]).setVisible(true);   // m[1] = 내 표시 이름
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
