package Project.Ui;

import Project.Game.GameRule;
import Project.Protocol.ChatMessageData;
import Project.Protocol.MessageProtocol;
import Project.User.Client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 게임방 화면: 참가자 목록, 채팅/알림 창, 게임 시작(방장만), 나가기(대기실로)
// 게임 중: 세션(밤/낮/투표)과 남은 시간 표시, 오른쪽 목록에서 대상을 골라
//          [능력 사용 / 투표] 버튼, 낮 토론에는 [시간 연장] 버튼.
// 채팅 가능 여부(사망자, 밤의 비마피아)는 서버가 최종 판단하고, 화면은 입력창을 막아서 안내만 한다.
public class GameRoomFrame extends JFrame {
    private final Client client;   // null이면 서버 없이 화면만 보기
    private final String displayName;

    private final JLabel lblPhase = new JLabel("대기중");
    private final JLabel lblTimer = new JLabel(" ");
    private final JLabel lblRole = new JLabel("내 직업: -");
    private final JLabel lblPlayerTitle = new JLabel("참가자 (0/" + GameRule.MAX_PLAYERS + ")", SwingConstants.CENTER);
    private final DefaultListModel<String> playerModel = new DefaultListModel<>();
    private final JList<String> playerList = new JList<>(playerModel);
    private final ChatPanel chat = new ChatPanel();
    private final JTextField txtChat = new JTextField();
    private final JButton btnSend = new JButton("전송");
    private final JButton btnStart = new JButton("게임 시작");
    private final JButton btnAction = new JButton("능력 / 투표");
    private final JButton btnExtend = new JButton("시간 연장");
    private final JButton btnLeave = new JButton("나가기");

    // ---- 화면이 알고 있는 상태 (서버 메시지로 갱신) ----
    private final List<String> names = new ArrayList<>();      // 참가자 이름 (목록 순서와 같음)
    private String host = "";
    private boolean playing = false;
    private String phase = "";                                  // NIGHT / DAY / VOTE
    private int day = 0;
    private String myRole = "";
    private final Set<String> alive = new HashSet<>();
    private final Set<String> mafiaTeam = new HashSet<>();      // 내가 마피아일 때만 채워진다
    private boolean extendUsed = false;                         // 오늘 시간 연장을 썼는지

    // 화면만 확인하는 미리보기용 main (서버 실행 없이 이 파일만 실행하면 된다)
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameRoomFrame f = new GameRoomFrame(null, "철수", 1, "초보환영");
            f.onMessage("ROOM_USERS|철수,철수,영희,민수,지수");
            f.onMessage("GAME_STARTED|마피아 1명, 경찰 1명, 의사 1명, 시민 1명");
            f.onMessage("ROLE|마피아");
            f.onMessage("MAFIA_TEAM|철수");
            f.onMessage("ALIVE|철수,영희,민수,지수");
            f.onMessage("PHASE|NIGHT,1,30");
            f.onMessage("ROOM_MSG|[알림] 1일차 밤이 되었습니다. (30초)");
            f.onMessage("CHAT|MAFIA_CHAT,철수,누구로 할까?");
            f.setVisible(true);
        });
    }

    public GameRoomFrame(Client client, String displayName, int roomId, String title) {
        super("게임방 [" + roomId + "] " + title + " - " + displayName);
        this.client = client;
        this.displayName = displayName;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(720, 540);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        root.add(buildTop(roomId, title), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildBottom(), BorderLayout.SOUTH);

        setupEvents();
        updateControls();

        if (client != null) {
            client.setListener(this::onMessage);   // 이제부터 이 화면이 서버 메시지를 받는다
        }
    }

    // 상단: 방 제목 / 세션 / 남은 시간 / 내 직업
    private JPanel buildTop(int roomId, String title) {
        JLabel lblTitle = new JLabel("[" + roomId + "번 방] " + title);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 15f));

        lblPhase.setFont(lblPhase.getFont().deriveFont(Font.BOLD, 14f));
        lblTimer.setFont(lblTimer.getFont().deriveFont(Font.BOLD, 14f));
        lblRole.setFont(lblRole.getFont().deriveFont(Font.BOLD, 14f));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        right.add(lblPhase);
        right.add(lblTimer);
        right.add(lblRole);

        JPanel top = new JPanel(new BorderLayout());
        top.add(lblTitle, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    // 중앙: 왼쪽 채팅/알림 + 오른쪽 참가자 목록(대상 선택)
    private JPanel buildCenter() {
        playerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel hint = new JLabel("대상은 목록에서 클릭", SwingConstants.CENTER);
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(Color.GRAY);

        JPanel userPanel = new JPanel(new BorderLayout(0, 5));
        userPanel.add(lblPlayerTitle, BorderLayout.NORTH);
        userPanel.add(new JScrollPane(playerList), BorderLayout.CENTER);
        userPanel.add(hint, BorderLayout.SOUTH);
        userPanel.setPreferredSize(new Dimension(200, 0));

        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.add(chat, BorderLayout.CENTER);   // ChatPanel이 스크롤을 직접 가지고 있다
        center.add(userPanel, BorderLayout.EAST);
        return center;
    }

    // 하단: 채팅 입력줄 + 버튼 4개
    private JPanel buildBottom() {
        JPanel chatRow = new JPanel(new BorderLayout(5, 0));
        chatRow.add(txtChat, BorderLayout.CENTER);
        chatRow.add(btnSend, BorderLayout.EAST);

        JPanel buttons = new JPanel(new GridLayout(1, 4, 8, 0));
        buttons.setPreferredSize(new Dimension(0, 45));
        buttons.add(btnStart);
        buttons.add(btnAction);
        buttons.add(btnExtend);
        buttons.add(btnLeave);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.add(chatRow, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        return bottom;
    }

    private void setupEvents() {
        btnSend.addActionListener(e -> sendChat());
        txtChat.addActionListener(e -> sendChat());   // Enter로 전송
        btnStart.addActionListener(e -> send(MessageProtocol.GAME_START));
        btnAction.addActionListener(e -> useAbilityOrVote());
        btnExtend.addActionListener(e -> {
            send(MessageProtocol.EXTEND_TIME);
            extendUsed = true;   // 하루 1번. 다음 낮에 다시 켜진다
            updateControls();
        });
        btnLeave.addActionListener(e -> leaveRoom());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logout();   // 이후 EXIT_ON_CLOSE로 프로그램 종료
            }
        });
    }

    // ==================== 화면 갱신 ====================

    private boolean amAlive() {
        return alive.contains(displayName);
    }

    private boolean isMafia() {
        return "마피아".equals(myRole);
    }

    // 참가자 목록 다시 그리기 (선택한 사람은 유지)
    private void refreshPlayerList() {
        int sel = playerList.getSelectedIndex();
        playerModel.clear();
        for (String n : names) {
            StringBuilder label = new StringBuilder(n);
            if (n.equals(host)) label.append(" (방장)");
            if (playing && !alive.contains(n)) label.append(" (사망)");
            if (mafiaTeam.contains(n)) label.append(" [마피아]");
            if (n.equals(displayName)) label.append(" ← 나");
            playerModel.addElement(label.toString());
        }
        if (sel >= 0 && sel < playerModel.size()) playerList.setSelectedIndex(sel);
        lblPlayerTitle.setText("참가자 (" + names.size() + "/" + GameRule.MAX_PLAYERS + ")");
    }

    // 버튼·채팅창을 현재 상태에 맞게 켜고 끈다.
    private void updateControls() {
        boolean isHost = host.equals(displayName);

        // 게임 시작: 방장 + 대기중 + 최소 인원 이상
        if (playing) {
            btnStart.setText("게임 진행 중");
            btnStart.setEnabled(false);
        } else if (!isHost) {
            btnStart.setText("방장이 시작합니다");
            btnStart.setEnabled(false);
        } else if (names.size() < GameRule.MIN_PLAYERS) {
            btnStart.setText("게임 시작 (" + GameRule.MIN_PLAYERS + "명부터)");
            btnStart.setEnabled(false);
        } else {
            btnStart.setText("게임 시작");
            btnStart.setEnabled(true);
        }

        // 능력 / 투표: 세션과 내 직업에 따라 바뀐다
        String actionText;
        boolean actionOn = false;
        if (!playing) {
            actionText = "능력 / 투표";
        } else if (!amAlive()) {
            actionText = "사망 (관전 중)";
        } else if (MessageProtocol.PHASE_NIGHT.equals(phase)) {
            switch (myRole) {
                case "마피아": actionText = "처치 대상 지목"; actionOn = true; break;
                case "의사":   actionText = "치료하기"; actionOn = true; break;
                case "경찰":   actionText = "조사하기"; actionOn = true; break;
                default:       actionText = "밤 (능력 없음)";
            }
        } else if (MessageProtocol.PHASE_VOTE.equals(phase)) {
            actionText = "투표하기";
            actionOn = true;
        } else {
            actionText = "토론 중";
        }
        btnAction.setText(actionText);
        btnAction.setEnabled(actionOn);

        // 시간 연장: 낮 토론 + 생존 + 오늘 아직 안 씀
        btnExtend.setText("시간 연장 +" + GameRule.EXTEND_SECONDS + "초");
        btnExtend.setEnabled(playing && amAlive() && MessageProtocol.PHASE_DAY.equals(phase) && !extendUsed);

        // 채팅: 게임 밖에서는 항상 가능. 게임 중에는 생존자만, 밤에는 마피아만.
        boolean canChat = !playing || (amAlive() && (!MessageProtocol.PHASE_NIGHT.equals(phase) || isMafia()));
        txtChat.setEnabled(canChat);
        btnSend.setEnabled(canChat);
        if (!playing || canChat) {
            txtChat.setToolTipText(isMafia() && MessageProtocol.PHASE_NIGHT.equals(phase)
                    ? "밤에는 마피아끼리만 보입니다" : null);
        } else {
            txtChat.setToolTipText(!amAlive() ? "사망한 플레이어는 채팅할 수 없습니다" : "밤에는 마피아만 대화할 수 있습니다");
        }
    }

    private void showPhase(String p, int d, int seconds) {
        phase = p;
        day = d;
        String text;
        Color color;
        switch (p) {
            case MessageProtocol.PHASE_NIGHT: text = d + "일차 밤";  color = new Color(60, 60, 150); break;
            case MessageProtocol.PHASE_DAY:   text = d + "일차 낮 (토론)"; color = new Color(200, 120, 0); break;
            default:                          text = d + "일차 투표"; color = new Color(170, 0, 0);
        }
        if (MessageProtocol.PHASE_DAY.equals(p)) extendUsed = false;   // 새 낮마다 연장 1회
        lblPhase.setText(text);
        lblPhase.setForeground(color);
        showTime(seconds);
        updateControls();
    }

    private void showTime(int seconds) {
        lblTimer.setText("남은 시간 " + seconds + "초");
        lblTimer.setForeground(seconds <= 5 ? new Color(200, 0, 0) : Color.DARK_GRAY);
    }

    private void showRole(String role) {
        myRole = role;
        lblRole.setText("내 직업: " + role);
        lblRole.setForeground("마피아".equals(role) ? new Color(190, 0, 0) : new Color(0, 70, 160));
    }

    // ==================== 서버 통신 ====================

    private void send(String msg) {
        if (client != null) client.send(msg);
    }

    private void sendChat() {
        String text = txtChat.getText().trim();
        if (text.isEmpty()) return;
        if (client != null) {
            client.send(MessageProtocol.build(MessageProtocol.ROOM_CHAT, text));
        } else {
            chat.addMessage(displayName, text, true);   // 미리보기
        }
        txtChat.setText("");
    }

    // 목록에서 고른 사람에게 능력 사용(밤) 또는 투표(투표 시간)
    private void useAbilityOrVote() {
        int idx = playerList.getSelectedIndex();
        if (idx < 0 || idx >= names.size()) {
            JOptionPane.showMessageDialog(this, "오른쪽 참가자 목록에서 대상을 먼저 선택하세요.");
            return;
        }
        String target = names.get(idx);
        String cmd = MessageProtocol.PHASE_VOTE.equals(phase) ? MessageProtocol.VOTE : MessageProtocol.ACTION;
        send(MessageProtocol.build(cmd, target));
    }

    // 대기실로 돌아가기. 대기실 화면이 먼저 리스너를 잡은 뒤 ROOM_LEAVE를 보낸다.
    private void leaveRoom() {
        if (playing && amAlive()) {
            int ok = JOptionPane.showConfirmDialog(this, "게임 중에 나가면 사망 처리됩니다. 나가시겠습니까?",
                    "나가기", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        if (client == null) {
            dispose();
            return;
        }
        WaitingRoomFrame w = new WaitingRoomFrame(client, displayName);
        w.setLocationRelativeTo(this);
        w.setVisible(true);
        dispose();
        client.send(MessageProtocol.ROOM_LEAVE);
    }

    private void logout() {
        if (client == null) return;
        client.setListener(null);
        client.send(MessageProtocol.LOGOUT);
        client.close();
    }

    private void onMessage(String msg) {
        String[] m = MessageProtocol.parse(msg);
        String body = m[1];
        switch (m[0]) {
            case MessageProtocol.ROOM_USERS: {   // "방장,참가자1,참가자2,..." (첫 값 방장, 이후 방장 포함 전체 참가자)
                String[] f = body.split(MessageProtocol.FIELD_SEP);
                host = f[0];
                names.clear();
                for (int i = 1; i < f.length; i++) names.add(f[i]);
                refreshPlayerList();
                updateControls();
                break;
            }
            case MessageProtocol.CHAT:
                ChatMessageData data = ChatMessageData.fromBody(body);
                if (data == null) break;                       // 형식이 잘못된 메시지는 무시
                String name = data.getSender();
                if (data.getType() == ChatMessageData.Type.MAFIA_CHAT) name += " (마피아)";
                chat.addMessage(name, data.getText(), data.getSender().equals(displayName));
                break;
            case MessageProtocol.ROOM_MSG:
                chat.addNotice(body);                          // 이제 ROOM_MSG는 모두 알림
                break;
            case MessageProtocol.GAME_STARTED:
                playing = true;
                myRole = "";
                mafiaTeam.clear();
                alive.clear();
                alive.addAll(names);
                extendUsed = false;
                refreshPlayerList();
                updateControls();
                break;
            case MessageProtocol.ROLE:
                showRole(body);
                updateControls();
                break;
            case MessageProtocol.MAFIA_TEAM:
                mafiaTeam.clear();
                for (String n : body.split(MessageProtocol.FIELD_SEP)) if (!n.isEmpty()) mafiaTeam.add(n);
                refreshPlayerList();
                break;
            case MessageProtocol.ALIVE:
                alive.clear();
                for (String n : body.split(MessageProtocol.FIELD_SEP)) if (!n.isEmpty()) alive.add(n);
                refreshPlayerList();
                updateControls();
                break;
            case MessageProtocol.PHASE: {   // "NIGHT,1,30"
                String[] f = body.split(MessageProtocol.FIELD_SEP);
                showPhase(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                break;
            }
            case MessageProtocol.TIME:
                showTime(Integer.parseInt(body));
                break;
            case MessageProtocol.GAME_END:
                playing = false;
                phase = "";
                alive.clear();
                mafiaTeam.clear();
                lblPhase.setText("게임 종료");
                lblPhase.setForeground(Color.DARK_GRAY);
                lblTimer.setText(" ");
                refreshPlayerList();
                updateControls();
                JOptionPane.showMessageDialog(this, body, "게임 종료", JOptionPane.INFORMATION_MESSAGE);
                break;
            case MessageProtocol.ROOM_FAIL:
                JOptionPane.showMessageDialog(this, body, "알림", JOptionPane.WARNING_MESSAGE);
                break;
            case MessageProtocol.DISCONNECTED:
                LoginFrame.askReconnect(this, displayName);
                break;
        }
    }


}
