package Project.Ui;

import Project.Protocol.MessageProtocol;
import Project.User.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

//대기실 화면, 방목록, 대기실접속자, 방생성/입장/종료 버튼을 추가
//방 생성(CreateRoomDialog) / 방 입장(비밀번호 확인) -> 서버가 ROOM_ENTER를 보내면 GameRoomFrame으로 이동
public class WaitingRoomFrame extends JFrame {
    private final Client client; // null이라면 서버없이 화면만 보기
    private final String displayName;

    // 서버에서 받은 방 정보 원본. 표의 행 순서와 같다. {번호, 제목, 현재인원, 최대인원, 상태, 비밀(1/0)}
    private final List<String[]> rooms = new ArrayList<>();

    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JLabel lblUserTitle = new JLabel("대기실 접속자 (0명)", SwingConstants.CENTER);

    // 방 목록 표 (사용자가 직접 수정하지 못하게 막는다)
    private final DefaultTableModel roomModel =
            new DefaultTableModel(new String[]{"번호", "방 제목", "인원", "상태"}, 0) {
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
            // 서버가 보내는 ROOM_LIST 내용과 같은 형식
            f.applyRoomList("1,초보환영,3,8,WAITING,0;2,고수만,8,8,PLAYING,1;3,친구끼리,2,8,WAITING,1");
            f.setVisible(true);
        });
    }

    public WaitingRoomFrame(Client client, String displayName) {
        super("대기실 - " + displayName);
        this.client = client;
        this.displayName = displayName;

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

        // 하단: 버튼
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
        roomTable.getColumnModel().getColumn(3).setMaxWidth(70);

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
        roomTable.addMouseListener(new MouseAdapter() {   // 방 목록 더블클릭으로도 입장
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && roomTable.getSelectedRow() >= 0) onJoinRoom();
            }
        });
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

    // ---- 버튼 동작 ----

    // 방 생성: 제목/비밀번호를 입력받아 ROOM_CREATE 전송. 성공하면 서버가 ROOM_ENTER를 보낸다.
    private void onCreateRoom() {
        String[] input = CreateRoomDialog.open(this);   // {제목, 비밀번호} 또는 취소 시 null
        if (input == null) return;
        if (client == null) {
            JOptionPane.showMessageDialog(this, "미리보기 모드에서는 방을 만들 수 없습니다.");
            return;
        }
        client.send(MessageProtocol.build(MessageProtocol.ROOM_CREATE,
                input[0] + MessageProtocol.FIELD_SEP + input[1]));
    }

    // 방 입장: 선택한 방이 비밀방이면 비밀번호를 물어본 뒤 ROOM_JOIN 전송
    private void onJoinRoom() {
        int row = roomTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "입장할 방을 먼저 선택하세요.");
            return;
        }
        String[] room = rooms.get(row);
        if (MessageProtocol.STATE_PLAYING.equals(room[4])) {
            JOptionPane.showMessageDialog(this, "이미 게임이 시작된 방입니다.");
            return;
        }
        if (room[2].equals(room[3])) {
            JOptionPane.showMessageDialog(this, "방이 가득 찼습니다.");
            return;
        }

        String password = "";
        if ("1".equals(room[5])) {
            JPasswordField pf = new JPasswordField();
            int ok = JOptionPane.showConfirmDialog(this, pf, "[" + room[1] + "] 비밀번호 입력",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            password = new String(pf.getPassword()).trim();
            if (MessageProtocol.hasReservedChar(password)) {
                JOptionPane.showMessageDialog(this, "비밀번호가 틀렸습니다.");
                return;
            }
        }
        if (client == null) {
            JOptionPane.showMessageDialog(this, "미리보기 모드에서는 입장할 수 없습니다.");
            return;
        }
        client.send(MessageProtocol.build(MessageProtocol.ROOM_JOIN,
                room[0] + MessageProtocol.FIELD_SEP + password));
    }

    // 게임방 화면으로 이동. 새 화면이 먼저 리스너를 잡으므로 뒤따라오는 메시지는 게임방이 받는다.
    private void openGameRoom(String body) {
        String[] f = body.split(MessageProtocol.FIELD_SEP, 2);
        GameRoomFrame room = new GameRoomFrame(client, displayName, Integer.parseInt(f[0]), f[1]);
        room.setLocationRelativeTo(this);
        room.setVisible(true);
        dispose();
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

    // 방 목록 갱신. 한 행 = {번호, 방 제목, 인원, 상태}
    public void updateRoomList(List<String[]> rows) {
        roomModel.setRowCount(0);
        for (String[] row : rows) {
            roomModel.addRow(row);
        }
    }

    // 서버의 ROOM_LIST 내용을 표에 반영. 형식: "번호,제목,현재,최대,상태,비밀;..."
    public void applyRoomList(String body) {
        int selectedId = -1;   // 갱신 후에도 선택한 방을 유지하기 위해 기억
        int sel = roomTable.getSelectedRow();
        if (sel >= 0 && sel < rooms.size()) selectedId = Integer.parseInt(rooms.get(sel)[0]);

        rooms.clear();
        List<String[]> display = new ArrayList<>();
        if (!body.isEmpty()) {
            for (String row : body.split(MessageProtocol.ROW_SEP)) {
                String[] f = row.split(MessageProtocol.FIELD_SEP, -1);
                if (f.length < 6) continue;
                rooms.add(f);
                String title = "1".equals(f[5]) ? "[비밀] " + f[1] : f[1];
                String state = MessageProtocol.STATE_PLAYING.equals(f[4]) ? "게임중" : "대기중";
                display.add(new String[]{f[0], title, f[2] + "/" + f[3], state});
            }
        }
        updateRoomList(display);

        for (int i = 0; i < rooms.size(); i++) {
            if (Integer.parseInt(rooms.get(i)[0]) == selectedId) {
                roomTable.setRowSelectionInterval(i, i);
                break;
            }
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
            case MessageProtocol.ROOM_LIST:
                applyRoomList(m[1]);
                break;
            case MessageProtocol.ROOM_ENTER:
                openGameRoom(m[1]);
                break;
            case MessageProtocol.ROOM_FAIL:
                JOptionPane.showMessageDialog(this, m[1], "알림", JOptionPane.WARNING_MESSAGE);
                break;
            case MessageProtocol.DISCONNECTED:
                JOptionPane.showMessageDialog(this, "서버와 연결이 끊어졌습니다.");
                System.exit(0);
                break;
        }
    }
}
