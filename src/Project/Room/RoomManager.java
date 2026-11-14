package Project.Room;

import Project.Game.GameRule;
import Project.Game.MafiaGame;
import Project.Protocol.MessageProtocol;
import Project.Server.ClientHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

// 모든 게임방을 관리한다. (생성 / 입장 / 퇴장 / 채팅 / 게임 시작 / 게임 중 요청 / 세션 타이머)
//
// 잠금 규칙: 방에 관한 작업은 모두 이 객체 하나로 잠근다(synchronized). 1초 타이머도 같은 잠금을 쓴다.
// 잠금 순서는 항상 RoomManager -> WaitingRoom -> 소켓 출력 순서이므로 교착 상태가 생기지 않는다.
public class RoomManager {
    private final Map<Integer, GameRoom> rooms = new LinkedHashMap<>();   // 생성 순서 유지
    private final WaitingRoom waitingRoom;
    private final Consumer<String> log;   // 서버 로그 창 출력용
    private int sequence = 0;

    // 게임 중인 방의 세션 시간을 1초마다 줄이는 타이머 (서버 전체에 1개)
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-timer");
        t.setDaemon(true);
        return t;
    });

    public RoomManager(WaitingRoom waitingRoom, Consumer<String> log) {
        this.waitingRoom = waitingRoom;
        this.log = log;
        timer.scheduleAtFixedRate(() -> {
            try {
                tick();
            } catch (Exception e) {   // 예외가 나도 타이머가 멈추지 않게 잡는다
                log.accept("타이머 처리 중 예외: " + e);
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // 1초마다: 게임 중인 방의 남은 시간을 줄이고, 끝나면 다음 세션으로 (MafiaGame.tick)
    private synchronized void tick() {
        for (GameRoom room : new ArrayList<>(rooms.values())) {
            MafiaGame game = room.getGame();
            if (game == null) continue;
            game.tick();
            endGameIfFinished(room);
        }
    }

    // 게임 중 요청: ACTION(밤 능력) / VOTE(투표) / EXTEND_TIME(토론 시간 연장)
    public synchronized void gameAction(ClientHandler user, String cmd, String target) {
        GameRoom room = user.getCurrentRoom();
        if (room == null || room.getGame() == null) {
            fail(user, "게임 중이 아닙니다.");
            return;
        }
        String error = room.getGame().handle(user, cmd, target);
        if (error != null) {
            fail(user, error);
            return;
        }
        endGameIfFinished(room);
    }

    // 방 생성. 만든 사람이 방장이 되어 바로 입장한다.
    public synchronized void createRoom(ClientHandler user, String title, String password) {
        if (user.getCurrentRoom() != null) {
            fail(user, "이미 게임방에 들어가 있습니다.");
            return;
        }
        GameRoom room = new GameRoom(++sequence, title, password);
        rooms.put(room.getId(), room);
        enter(user, room);
        room.notice(user.getDisplayName() + "님이 방을 만들었습니다. "
                + GameRule.MIN_PLAYERS + "명 이상 모이면 방장이 게임을 시작할 수 있습니다.");
        log.accept("방 생성: [" + room.getId() + "] " + title
                + (room.isLocked() ? " (비밀방)" : "") + " / 방장 " + user.getDisplayName());
        broadcastRoomList();
    }

    // 방 입장
    public synchronized void joinRoom(ClientHandler user, int roomId, String password) {
        if (user.getCurrentRoom() != null) {
            fail(user, "이미 게임방에 들어가 있습니다.");
            return;
        }
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            fail(user, "존재하지 않는 방입니다.");
            return;
        }
        if (room.getState() == GameRoom.State.PLAYING) {
            fail(user, "이미 게임이 시작된 방입니다.");
            return;
        }
        if (room.isFull()) {
            fail(user, "방이 가득 찼습니다. (최대 " + GameRule.MAX_PLAYERS + "명)");
            return;
        }
        if (!room.checkPassword(password)) {
            fail(user, "비밀번호가 틀렸습니다.");
            return;
        }
        enter(user, room);
        room.notice(user.getDisplayName() + "님이 입장했습니다. (" + room.size() + "/" + GameRule.MAX_PLAYERS + ")");
        log.accept("방 입장: [" + room.getId() + "] " + user.getDisplayName());
        broadcastRoomList();
    }

    // 방 퇴장. backToWaitingRoom이 false면 접속 종료로 나가는 경우다.
    public synchronized void leaveRoom(ClientHandler user, boolean backToWaitingRoom) {
        GameRoom room = user.getCurrentRoom();
        if (room == null) return;

        user.setCurrentRoom(null);
        ClientHandler oldHost = room.getHost();
        room.remove(user);
        log.accept("방 퇴장: [" + room.getId() + "] " + user.getDisplayName());

        if (room.isEmpty()) {
            rooms.remove(room.getId());
            log.accept("방 삭제: [" + room.getId() + "] " + room.getTitle());
        } else {
            room.notice(user.getDisplayName() + "님이 나갔습니다.");
            if (oldHost == user) {
                room.notice(room.getHost().getDisplayName() + "님이 새 방장이 되었습니다.");
            }
            room.broadcastUsers();
            if (room.getGame() != null) {
                room.getGame().onPlayerLeft(user);   // 게임 중이면 사망 처리 + 승패 확인
                endGameIfFinished(room);
            }
        }

        if (backToWaitingRoom) {
            waitingRoom.enter(user);
        }
        broadcastRoomList();
    }

    // 방 채팅
    public synchronized void chat(ClientHandler user, String text) {
        GameRoom room = user.getCurrentRoom();
        if (room == null) return;
        room.chat(user, text);
    }

    // 게임 시작 (방장만)
    public synchronized void startGame(ClientHandler user) {
        GameRoom room = user.getCurrentRoom();
        if (room == null) {
            fail(user, "게임방에 들어가 있지 않습니다.");
            return;
        }
        String error = room.startGame(user);
        if (error != null) {
            fail(user, error);
            return;
        }
        log.accept("게임 시작: [" + room.getId() + "] " + room.getTitle() + " (" + room.size() + "명)");
        broadcastRoomList();   // 대기실에 "게임중" 표시
    }

    // 한 사람에게 현재 방 목록 전송 (대기실에 처음 들어왔을 때)
    public synchronized void sendRoomList(ClientHandler user) {
        user.send(roomListMessage());
    }

    // ---- 내부 ----

    // 게임이 끝났으면 방을 대기 상태로 되돌리고 대기실 목록을 갱신한다.
    private void endGameIfFinished(GameRoom room) {
        MafiaGame game = room.getGame();
        if (game == null || !game.isFinished()) return;
        room.endGame();
        log.accept("게임 종료: [" + room.getId() + "] " + room.getTitle());
        broadcastRoomList();
    }

    // 대기실 -> 게임방 이동 공통 처리
    private void enter(ClientHandler user, GameRoom room) {
        waitingRoom.leave(user);
        room.add(user);
        user.setCurrentRoom(room);
        user.send(MessageProtocol.build(MessageProtocol.ROOM_ENTER,
                room.getId() + MessageProtocol.FIELD_SEP + room.getTitle()));
        room.broadcastUsers();
    }

    // 대기실 전원에게 방 목록 전송
    private void broadcastRoomList() {
        waitingRoom.broadcastAll(roomListMessage());
    }

    private String roomListMessage() {
        StringBuilder sb = new StringBuilder();
        for (GameRoom room : rooms.values()) {
            if (sb.length() > 0) sb.append(MessageProtocol.ROW_SEP);
            sb.append(room.toListRow());
        }
        return MessageProtocol.build(MessageProtocol.ROOM_LIST, sb.toString());
    }

    private void fail(ClientHandler user, String reason) {
        user.send(MessageProtocol.build(MessageProtocol.ROOM_FAIL, reason));
    }
}
