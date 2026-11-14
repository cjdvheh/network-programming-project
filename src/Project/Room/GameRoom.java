package Project.Room;

import Project.Game.GameRule;
import Project.Game.MafiaGame;
import Project.Protocol.ChatMessageData;
import Project.Protocol.MessageProtocol;
import Project.Server.ClientHandler;

import java.util.ArrayList;
import java.util.List;

// 게임방 1개. 참가자, 방장, 진행 상태를 관리한다. 게임 진행 자체는 MafiaGame이 맡는다.
// 주의: 이 클래스는 따로 잠그지 않는다. 모든 메서드는 RoomManager의 잠금 안에서만 호출된다.
//       (방마다 잠금을 따로 두면 대기실 잠금과 순서가 꼬여 교착 상태가 생기기 쉽다.)
public class GameRoom {
    public enum State { WAITING, PLAYING }

    private final int id;
    private final String title;
    private final String password;   // 빈 문자열이면 공개방

    private final List<ClientHandler> players = new ArrayList<>();   // 입장 순서
    private ClientHandler host;
    private State state = State.WAITING;
    private MafiaGame game = null;   // 게임 중이 아니면 null

    public GameRoom(int id, String title, String password) {
        this.id = id;
        this.title = title;
        this.password = password;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public State getState() { return state; }
    public ClientHandler getHost() { return host; }
    public MafiaGame getGame() { return game; }
    public int size() { return players.size(); }
    public boolean isEmpty() { return players.isEmpty(); }
    public boolean isFull() { return players.size() >= GameRule.MAX_PLAYERS; }
    public boolean isLocked() { return !password.isEmpty(); }
    public boolean contains(ClientHandler user) { return players.contains(user); }
    public List<ClientHandler> getPlayers() { return new ArrayList<>(players); }

    public boolean checkPassword(String input) {
        return password.isEmpty() || password.equals(input);
    }

    // 참가자 추가. 첫 참가자가 방장이 된다.
    public void add(ClientHandler user) {
        players.add(user);
        if (host == null) host = user;
    }

    // 참가자 제거. 방장이 나가면 가장 먼저 들어온 사람이 방장을 이어받는다.
    public void remove(ClientHandler user) {
        players.remove(user);
        if (host == user) {
            host = players.isEmpty() ? null : players.get(0);
        }
    }

    // ---- 전송 ----

    public void broadcast(String msg) {
        for (ClientHandler u : new ArrayList<>(players)) {
            u.send(msg);
        }
    }

    // 방 안 전원에게 알림 한 줄
    public void notice(String text) {
        broadcast(MessageProtocol.build(MessageProtocol.ROOM_MSG, "[알림] " + text));
    }

    // 채팅. 게임 중이면 MafiaGame의 규칙(사망자 금지, 밤에는 마피아끼리만)을 따른다.
    public void chat(ClientHandler from, String text) {
        if (game != null) {
            game.chat(from, text);
            return;
        }
        ChatMessageData data = new ChatMessageData(ChatMessageData.Type.CHAT, from.getDisplayName(), text);
        broadcast(MessageProtocol.build(MessageProtocol.CHAT, data.toBody()));

    }

    // 참가자 목록 전송. 형식: "방장,참가자1,참가자2,..." (첫 값은 방장, 이후는 방장을 포함한 전체 참가자)
    public void broadcastUsers() {
        StringBuilder sb = new StringBuilder(host == null ? "" : host.getDisplayName());
        for (ClientHandler u : players) {
            sb.append(MessageProtocol.FIELD_SEP).append(u.getDisplayName());
        }
        broadcast(MessageProtocol.build(MessageProtocol.ROOM_USERS, sb.toString()));
    }

    // 대기실 방 목록의 한 행. 형식: "번호,제목,현재인원,최대인원,상태,비밀(1/0)"
    public String toListRow() {
        String st = state == State.PLAYING ? MessageProtocol.STATE_PLAYING : MessageProtocol.STATE_WAITING;
        return String.join(MessageProtocol.FIELD_SEP,
                String.valueOf(id), title, String.valueOf(players.size()),
                String.valueOf(GameRule.MAX_PLAYERS), st, isLocked() ? "1" : "0");
    }

    // ---- 게임 ----

    // 게임 시작. 시작할 수 없으면 사유를, 시작했으면 null을 돌려준다.
    public String startGame(ClientHandler requester) {
        if (requester != host) return "방장만 게임을 시작할 수 있습니다.";
        if (state == State.PLAYING) return "이미 게임이 진행 중입니다.";
        if (players.size() < GameRule.MIN_PLAYERS) {
            return GameRule.MIN_PLAYERS + "명 이상 모여야 시작할 수 있습니다. (현재 " + players.size() + "명)";
        }
        state = State.PLAYING;
        game = new MafiaGame(this, players);
        game.start();   // 직업 배정 후 첫날 밤으로
        return null;
    }

    // 게임이 끝나면 대기 상태로 되돌린다. (같은 방에서 다시 시작 가능)
    public void endGame() {
        state = State.WAITING;
        game = null;
    }

}
