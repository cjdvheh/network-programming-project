package Project.Server;

import Project.Room.RoomManager;
import Project.Room.WaitingRoom;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

// 서버 실행 창. 포트를 입력받아 서버를 시작하고, 접속·로그인 기록을 로그창에 보여준다.
public class MainServer extends JFrame {
    private JTextArea textArea;
    private JTextField txtPortNumber;
    private ServerSocket serverSocket;
    JButton btnServerStart;
    private final ClientManager clientManager = new ClientManager();
    private final WaitingRoom waitingRoom = new WaitingRoom();
    private final RoomManager roomManager = new RoomManager(waitingRoom, this::appendLog);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainServer().setVisible(true));
    }

    public MainServer() {
        setTitle("Mafia Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 338, 386);
        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(12, 10, 300, 244);
        contentPane.add(scrollPane);

        textArea = new JTextArea();
        textArea.setEditable(false);
        scrollPane.setViewportView(textArea);

        JLabel lblPort = new JLabel("Port Number");
        lblPort.setBounds(12, 264, 87, 26);
        contentPane.add(lblPort);

        txtPortNumber = new JTextField("30000");
        txtPortNumber.setHorizontalAlignment(SwingConstants.CENTER);
        txtPortNumber.setBounds(111, 264, 199, 26);
        contentPane.add(txtPortNumber);

        btnServerStart = new JButton("Server Start");
        btnServerStart.setBounds(12, 300, 300, 35);
        contentPane.add(btnServerStart);
        btnServerStart.addActionListener(e -> startServer());
    }

    public void startServer() {
        if(serverSocket !=null) return;
        try{
            serverSocket = new ServerSocket(Integer.parseInt(txtPortNumber.getText().trim()));

        } catch (NumberFormatException | IOException ex) {
            appendLog("서버 시작 실패: " + ex.getMessage());
            return;
        }
        //appendLog("Mafia Server Running..");
        btnServerStart.setText("Mafia Server Running..");
        btnServerStart.setEnabled(false);
        txtPortNumber.setEnabled(false);
        new AcceptServer().start();
    }

    // 새 접속을 계속 받는 스레드
    class AcceptServer extends Thread {
        @Override
        public void run() {
            while (!serverSocket.isClosed()) {
                try {
                    appendLog("Waiting clients ...");
                    Socket clientSocket = serverSocket.accept();
                    appendLog("새로운 접속 from " + clientSocket);

                    ClientHandler handler = new ClientHandler(clientSocket, MainServer.this,
                            clientManager, waitingRoom, roomManager);
                    clientManager.add(handler);
                    handler.start();
                } catch (IOException e) {
                    appendLog("!!!! accept 에러 발생... !!!!");
                }
            }
        }
    }

    // 서버 로그 창에 출력 (여러 스레드에서 호출하므로 EDT에서 실행하도록 감쌌다)
    public void appendLog(String str) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(str + "\n");
            textArea.setCaretPosition(textArea.getText().length());
        });
    }
}