package Project.Ui;

import Project.Protocol.MessageProtocol;
import Project.User.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

//대기실 화면, 방목록, 대기실접속자, 방생성/입장/종료 버튼을 추가
//이번 단계 : UI만 구성 방 생성/압장 버튼의 동작은 다음 단계에서 연결
public class WaitingRoomFrame extends JFrame {
    private final Client client; // null이라면 서버없이 화면만 보기

    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JLabel lblUserTitle = new JLabel("대기실 접속자 (0명)", SwingConstants.CENTER);

    // 방 목록 표 (사용자가 직접 수정하지 못하게 막는다)
    private final DefaultTableModel roomModel =
            new DefaultTableModel(new String[]{"번호", "방 제목", "인원"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    private final JTable roomTable = new JTable(roomModel);

    private final JButton btnCreate = new JButton("방 생성");
    private final JButton btnJoin = new JButton("방 입장");
    private final JButton btnExit = new JButton("종료");

    // 화면만 확인하는 미리보기용 main (서버 실행 없이 이 파일만 실행하면 된다)
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WaitingRoomFrame f = new WaitingRoomFrame(null, "1_철수");
            f.updateUserList("1_철수,2_영희,3_민수");
            f.updateRoomList(List.of(
                    new String[]{"1", "초보환영", "3/8"},
                    new String[]{"2", "고수만", "8/8"}));
            f.setVisible(true);
        });
    }

    public WaitingRoomFrame(Client client, String displayName) {
        super("대기실 - " + displayName);
        this.client = client;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(640, 500);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        // 상단: 내 이름
        JLabel lblMe = new JLabel("내 이름: " + displayName);
        lblMe.setFont(lblMe.getFont().deriveFont(Font.BOLD, 14f));
        root.add(lblMe, BorderLayout.NORTH);

        // 중앙: 왼쪽 방 목록 + 오른쪽 접속자 목록
        root.add(buildCenter(), BorderLayout.CENTER);

        // 하단: 버튼 (이 블록이 빠져 있었음)
        JPanel buttons = new JPanel(new GridLayout(1, 3, 10, 0));
        buttons.setPreferredSize(new Dimension(0, 45));   // 버튼 줄 높이 고정
        buttons.add(btnCreate);
        buttons.add(btnJoin);
        buttons.add(btnExit);
        root.add(buttons, BorderLayout.SOUTH);

        setupEvents();

        if (client != null) {
            client.setListener(this::onMessage);   // 이제부터 이 화면이 서버 메시지를 받는다
        }
    }

    // 중앙 영역 구성
    private JPanel buildCenter() {
        roomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomTable.getTableHeader().setReorderingAllowed(false);
        roomTable.getColumnModel().getColumn(0).setMaxWidth(50);
        roomTable.getColumnModel().getColumn(2).setMaxWidth(70);

        JPanel roomPanel = new JPanel(new BorderLayout(0, 5));
        roomPanel.add(new JLabel("게임방 목록", SwingConstants.CENTER), BorderLayout.NORTH);
        roomPanel.add(new JScrollPane(roomTable), BorderLayout.CENTER);

        JPanel userPanel = new JPanel(new BorderLayout(0, 5));
        userPanel.add(lblUserTitle, BorderLayout.NORTH);
        userPanel.add(new JScrollPane(new JList<>(userModel)), BorderLayout.CENTER);
        userPanel.setPreferredSize(new Dimension(180, 0));

        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.add(roomPanel, BorderLayout.CENTER);
        center.add(userPanel, BorderLayout.EAST);
        return center;
    }

    private void setupEvents() {
        btnCreate.addActionListener(e -> onCreateRoom());
        btnJoin.addActionListener(e -> onJoinRoom());
        btnExit.addActionListener(e -> {
            leave();
            System.exit(0);
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                leave();   // 이후 EXIT_ON_CLOSE로 프로그램 종료
            }
        });
    }

    // ---- 버튼 동작 (다음 단계에서 구현) ----

    private void onCreateRoom() {
        // TODO 다음 단계: CreateRoomFrame(방 제목, 비밀번호 입력)을 열고 ROOM_CREATE 전송
        JOptionPane.showMessageDialog(this, "방 생성 기능은 다음 단계에서 구현합니다.");
    }


    private void onJoinRoom() {
        int row = roomTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "입장할 방을 먼저 선택하세요.");
            return;
        }
        // TODO 다음 단계: JoinRoomFrame(비밀번호 입력)을 열고 ROOM_JOIN 전송
        JOptionPane.showMessageDialog(this, "방 입장 기능은 다음 단계에서 구현합니다.");
    }


    // ---- 화면 갱신 메서드 (서버 메시지 처리나 미리보기에서 호출) ----

    // 대기실 접속자 목록 갱신. 형식: "1_철수,2_영희"
    public void updateUserList(String csv) {
        userModel.clear();
        if (!csv.isEmpty()) {
            for (String name : csv.split(",")) {
                userModel.addElement(name);
            }
        }
        lblUserTitle.setText("대기실 접속자 (" + userModel.size() + "명)");
    }

    // 방 목록 갱신. 한 행 = {번호, 방 제목, 인원}
    public void updateRoomList(List<String[]> rows) {
        roomModel.setRowCount(0);
        for (String[] row : rows) {
            roomModel.addRow(row);
        }
    }

    // ---- 서버 통신 ----

    private void leave() {
        if (client == null) return;
        client.setListener(null);
        client.send(MessageProtocol.LOGOUT);
        client.close();
    }

    private void onMessage(String msg) {
        String[] m = MessageProtocol.parse(msg);
        switch (m[0]) {
            case MessageProtocol.WAITING_USERS:
                updateUserList(m[1]);
                break;
            case MessageProtocol.DISCONNECTED:
                JOptionPane.showMessageDialog(this, "서버와 연결이 끊어졌습니다.");
                System.exit(0);
                break;
        }
    }
}
