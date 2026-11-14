package Project.Game;

import Project.Protocol.ChatMessageData;
import Project.Protocol.MessageProtocol;
import Project.Room.GameRoom;
import Project.Server.ClientHandler;

import java.util.*;

// 마피아 게임 한 판의 진행을 담당한다.
//
//   직업 배정 → [밤] → [낮: 밤 결과 발표 + 토론] → [투표] → [밤] → ... (한쪽이 이길 때까지 반복)
//
// - 각 세션은 GameRule의 시간만큼 진행되고, RoomManager가 1초마다 tick()을 불러 시간이 끝나면 다음 세션으로 넘어간다.
// - 밤: 마피아는 처치할 사람 1명 지목(마피아가 여럿이면 마지막 지목이 적용), 의사는 살릴 사람 1명 선택,
//       경찰은 1명 조사(즉시 결과). 마피아 대상과 의사 대상이 같으면 생존, 다르면 사망.
// - 낮: 토론. 살아 있는 사람마다 하루 1번 시간 연장(+EXTEND_SECONDS초) 가능.
// - 투표: 최다 득표자 1명은 바로 사망. (동점이거나 아무도 투표하지 않으면 처형 없음)
// - 채팅: 사망자는 채팅 불가. 밤에는 마피아끼리만 채팅 가능하고 마피아와 사망자에게만 보인다.
//
// 주의: GameRoom과 마찬가지로 RoomManager의 잠금 안에서만 호출된다. (따로 잠그지 않음)
public class MafiaGame {
    public enum Phase { NIGHT, DAY, VOTE }

    private final GameRoom room;
    private final List<ClientHandler> participants;   // 게임을 시작한 참가자 (입장 순서)
    private final Map<ClientHandler, Role> roles = new HashMap<>();
    private final Set<ClientHandler> alive = new LinkedHashSet<>();

    private Phase phase;
    private int day = 0;          // 1일차 밤 → 1일차 낮 → 1일차 투표 → 2일차 밤 ...
    private int remaining = 0;    // 현재 세션 남은 시간(초)
    private boolean finished = false;

    // 밤 행동 (밤마다 초기화)
    private ClientHandler mafiaTarget;
    private ClientHandler doctorTarget;
    private final Set<ClientHandler> investigated = new HashSet<>();   // 이번 밤에 이미 조사한 경찰

    // 낮 토론 시간 연장을 이미 쓴 사람 (낮마다 초기화)
    private final Set<ClientHandler> extended = new HashSet<>();

    // 투표: 투표한 사람 -> 대상 (투표 시간마다 초기화)
    private final Map<ClientHandler, ClientHandler> votes = new LinkedHashMap<>();

    public MafiaGame(GameRoom room, List<ClientHandler> players) {
        this.room = room;
        this.participants = new ArrayList<>(players);
    }

    public boolean isFinished() { return finished; }

    // ==================== 시작 ====================

    public void start() {
        // 1) 직업 배정: 인원에 맞는 직업 목록을 섞어서 입장 순서대로 나눠준다.
        List<Role> deck = Role.distribute(participants.size());
        Collections.shuffle(deck);
        for (int i = 0; i < participants.size(); i++) {
            roles.put(participants.get(i), deck.get(i));
            alive.add(participants.get(i));
        }

        // 2) 전원에게 시작 알림 (직업 구성만 공개)
        String summary = roleSummary(deck);
        room.broadcast(MessageProtocol.build(MessageProtocol.GAME_STARTED, summary));
        room.notice("게임이 시작되었습니다! 직업 구성: " + summary);

        // 3) 각자에게 본인 직업만 전송. 마피아에게는 동료 목록도 보낸다.
        List<String> mafiaNames = new ArrayList<>();
        for (ClientHandler u : participants) {
            if (roles.get(u) == Role.MAFIA) mafiaNames.add(u.getDisplayName());
        }
        for (ClientHandler u : participants) {
            Role r = roles.get(u);
            u.send(MessageProtocol.build(MessageProtocol.ROLE, r.getDisplayName()));
            tell(u, "[직업] 당신은 " + r.getDisplayName() + "입니다. " + r.getDescription());
            if (r == Role.MAFIA) {
                u.send(MessageProtocol.build(MessageProtocol.MAFIA_TEAM, String.join(MessageProtocol.FIELD_SEP, mafiaNames)));
                tell(u, "[직업] " + mafiaTeamText(u, mafiaNames));
            }
        }
        broadcastAlive();

        // 4) 첫날 밤으로
        startNight();
    }

    // ==================== 타이머 ====================

    // RoomManager가 1초마다 호출한다.
    public void tick() {
        if (finished) return;
        remaining--;
        room.broadcast(MessageProtocol.build(MessageProtocol.TIME, String.valueOf(Math.max(remaining, 0))));
        if (remaining > 0) return;

        switch (phase) {
            case NIGHT: endNight(); break;
            case DAY:   startVote(); break;
            case VOTE:  endVote(); break;
        }
    }

    // ==================== 밤 ====================

    private void startNight() {
        day++;
        phase = Phase.NIGHT;
        remaining = GameRule.NIGHT_SECONDS;
        mafiaTarget = null;
        doctorTarget = null;
        investigated.clear();

        broadcastPhase();
        room.notice(day + "일차 밤이 되었습니다. (" + remaining + "초) 마피아·의사·경찰은 대상을 골라 능력을 사용하세요.");
        for (ClientHandler u : alive) {
            switch (roles.get(u)) {
                case MAFIA:
                    tell(u, "[마피아] 오른쪽 목록에서 처치할 사람을 고르고 '처치 대상 지목'을 누르세요. 밤에는 마피아끼리만 대화할 수 있습니다.");
                    break;
                case DOCTOR:
                    tell(u, "[의사] 살릴 사람을 고르세요. 마피아가 고른 사람과 같으면 그 사람은 살아납니다.");
                    break;
                case POLICE:
                    tell(u, "[경찰] 조사할 사람을 고르세요. 마피아인지 바로 알려드립니다. (밤마다 1명)");
                    break;
                default:
                    tell(u, "[시민] 밤에는 할 수 있는 일이 없습니다. 아침을 기다리세요.");
            }
        }
    }

    // 밤 결과 처리: 마피아 대상 == 의사 대상이면 생존, 아니면 사망
    private void endNight() {
        ClientHandler victim = mafiaTarget;
        ClientHandler dead = null;
        String result;
        if (victim == null || !alive.contains(victim)) {
            result = "지난 밤은 조용히 지나갔습니다. 아무도 죽지 않았습니다.";
        } else if (victim == doctorTarget) {
            result = "지난 밤 " + victim.getDisplayName() + "님이 마피아의 공격을 받았지만 의사의 치료로 살아났습니다!";
        } else {
            kill(victim);
            dead = victim;
            result = "지난 밤 " + victim.getDisplayName() + "님이 마피아에게 살해당했습니다.";
        }
        startDay(result, dead);
    }

    // ==================== 낮 (토론) ====================

    private void startDay(String nightResult, ClientHandler dead) {
        phase = Phase.DAY;
        remaining = GameRule.DISCUSSION_SECONDS;
        extended.clear();

        broadcastPhase();
        room.notice(day + "일차 낮이 밝았습니다. " + nightResult);
        tellDead(dead);
        if (checkWin()) return;
        room.notice("토론 시간입니다. (" + remaining + "초) 각자 직업을 밝히고 마피아를 추리하세요. "
                + "시간 연장은 한 사람당 하루 1번(+" + GameRule.EXTEND_SECONDS + "초) 가능합니다.");
    }

    // ==================== 투표 ====================

    private void startVote() {
        phase = Phase.VOTE;
        remaining = GameRule.VOTE_SECONDS;
        votes.clear();

        broadcastPhase();
        room.notice("투표 시간입니다. (" + remaining + "초) 처형할 사람을 고르고 '투표하기'를 누르세요. "
                + "가장 많은 표를 받은 사람은 바로 사망합니다.");
    }

    private void endVote() {
        // 집계 (살아 있는 사람의 표만, 살아 있는 대상만)
        Map<ClientHandler, Integer> count = new LinkedHashMap<>();
        int voters = 0;
        for (Map.Entry<ClientHandler, ClientHandler> e : votes.entrySet()) {
            if (alive.contains(e.getKey()) && alive.contains(e.getValue())) {
                count.merge(e.getValue(), 1, Integer::sum);
                voters++;
            }
        }
        int abstain = alive.size() - voters;

        if (count.isEmpty()) {
            room.notice("투표 결과: 아무도 투표하지 않아 처형이 없습니다.");
        } else {
            int max = Collections.max(count.values());
            List<ClientHandler> top = new ArrayList<>();
            StringBuilder tally = new StringBuilder();
            for (Map.Entry<ClientHandler, Integer> e : count.entrySet()) {
                if (e.getValue() == max) top.add(e.getKey());
                if (tally.length() > 0) tally.append(", ");
                tally.append(e.getKey().getDisplayName()).append(' ').append(e.getValue()).append("표");
            }
            if (abstain > 0) tally.append(", 기권 ").append(abstain).append("명");
            room.notice("투표 결과: " + tally);

            if (top.size() > 1) {
                room.notice("최다 득표가 동점이라 아무도 처형되지 않았습니다.");
            } else {
                ClientHandler executed = top.get(0);
                kill(executed);
                room.notice(executed.getDisplayName() + "님이 투표로 처형되었습니다.");
                tellDead(executed);
            }
        }
        if (checkWin()) return;
        startNight();
    }

    // ==================== 플레이어 요청 ====================

    // ACTION / VOTE / EXTEND_TIME 처리. 잘못된 요청이면 사유를, 성공하면 null을 돌려준다.
    public String handle(ClientHandler user, String cmd, String targetName) {
        if (finished) return "게임이 끝났습니다.";
        if (!alive.contains(user)) return "사망한 플레이어는 할 수 없습니다.";
        switch (cmd) {
            case MessageProtocol.ACTION:      return nightAction(user, targetName);
            case MessageProtocol.VOTE:        return vote(user, targetName);
            case MessageProtocol.EXTEND_TIME: return extend(user);
            default:                          return "알 수 없는 요청입니다.";
        }
    }

    private String nightAction(ClientHandler user, String targetName) {
        if (phase != Phase.NIGHT) return "능력은 밤에만 사용할 수 있습니다.";
        Role role = roles.get(user);
        if (role == Role.CITIZEN) return "시민은 밤에 사용할 능력이 없습니다.";

        ClientHandler target = findParticipant(targetName);
        if (target == null) return "존재하지 않는 플레이어입니다.";
        if (!alive.contains(target)) return "이미 사망한 플레이어는 선택할 수 없습니다.";

        switch (role) {
            case MAFIA:
                if (roles.get(target) == Role.MAFIA) return "마피아끼리는 지목할 수 없습니다.";
                mafiaTarget = target;   // 마피아가 여럿이면 마지막 지목이 적용된다
                tellMafia("[마피아] " + user.getDisplayName() + "님이 " + target.getDisplayName() + "님을 처치 대상으로 지목했습니다.");
                return null;
            case DOCTOR:
                doctorTarget = target;   // 밤 동안 바꿀 수 있다
                tell(user, "[의사] " + target.getDisplayName() + "님을 치료하기로 했습니다. 마피아가 고른 사람과 같으면 살아납니다.");
                return null;
            case POLICE:
                if (target == user) return "자기 자신은 조사할 수 없습니다.";
                if (investigated.contains(user)) return "오늘 밤에는 이미 조사했습니다. (밤마다 1명)";
                investigated.add(user);
                boolean isMafia = roles.get(target) == Role.MAFIA;
                tell(user, "[경찰] 조사 결과: " + target.getDisplayName() + "님은 "
                        + (isMafia ? "마피아입니다!" : "마피아가 아닙니다. (시민 팀)"));
                return null;
            default:
                return "사용할 수 있는 능력이 없습니다.";
        }
    }

    private String vote(ClientHandler user, String targetName) {
        if (phase != Phase.VOTE) return "지금은 투표 시간이 아닙니다.";
        ClientHandler target = findParticipant(targetName);
        if (target == null) return "존재하지 않는 플레이어입니다.";
        if (!alive.contains(target)) return "이미 사망한 플레이어에게는 투표할 수 없습니다.";
        if (target == user) return "자기 자신에게는 투표할 수 없습니다.";

        boolean changed = votes.containsKey(user);
        votes.put(user, target);   // 투표 시간 안에는 바꿀 수 있다
        tell(user, "[투표] " + target.getDisplayName() + "님에게 투표했습니다." + (changed ? " (변경)" : ""));
        if (!changed) {
            room.notice("투표 현황: " + votes.size() + "/" + alive.size() + "명 완료");
        }
        if (votes.size() >= alive.size()) {   // 모두 투표하면 바로 집계
            room.notice("모두 투표를 마쳤습니다.");
            endVote();
        }
        return null;
    }

    private String extend(ClientHandler user) {
        if (phase != Phase.DAY) return "시간 연장은 낮 토론 시간에만 할 수 있습니다.";
        if (extended.contains(user)) return "시간 연장은 하루에 한 번만 할 수 있습니다.";
        extended.add(user);
        remaining += GameRule.EXTEND_SECONDS;
        room.broadcast(MessageProtocol.build(MessageProtocol.TIME, String.valueOf(remaining)));
        room.notice(user.getDisplayName() + "님이 토론 시간을 " + GameRule.EXTEND_SECONDS
                + "초 연장했습니다. (남은 시간 " + remaining + "초)");
        return null;
    }

    // ==================== 채팅 ====================

    // 게임 중 채팅. 사망자는 쓸 수 없고(보기는 가능), 밤에는 마피아끼리만 대화한다.
    public void chat(ClientHandler user, String text) {
        if (!alive.contains(user)) {
            tell(user, "[알림] 사망한 플레이어는 채팅할 수 없습니다.");
            return;
        }
        if (phase == Phase.NIGHT) {
            if (roles.get(user) != Role.MAFIA) {
                tell(user, "[알림] 밤에는 마피아만 대화할 수 있습니다.");
                return;
            }
            sendToMafiaAndDead(chatMessage(ChatMessageData.Type.MAFIA_CHAT, user, text));
            return;
        }
        room.broadcast(chatMessage(ChatMessageData.Type.CHAT, user, text));
    }

    // 채팅 한 줄을 전송용 문자열로 만든다. 예) "CHAT|CHAT,영희,안녕"
    private String chatMessage(ChatMessageData.Type type, ClientHandler user, String text) {
        ChatMessageData data = new ChatMessageData(type, user.getDisplayName(), text);
        return MessageProtocol.build(MessageProtocol.CHAT, data.toBody());
    }
    // ==================== 퇴장 ====================

    // 게임 중 방을 나가거나 연결이 끊긴 경우: 사망 처리 후 승패 확인
    public void onPlayerLeft(ClientHandler user) {
        if (finished || !alive.contains(user)) return;
        alive.remove(user);
        votes.remove(user);
        votes.values().removeIf(t -> t == user);   // 이 사람에게 던진 표는 무효 → 다시 투표 가능
        if (mafiaTarget == user) mafiaTarget = null;
        if (doctorTarget == user) doctorTarget = null;
        broadcastAlive();
        room.notice(user.getDisplayName() + "님이 게임 중에 나가 사망 처리되었습니다.");

        if (checkWin()) return;
        if (phase == Phase.VOTE && !alive.isEmpty() && votes.size() >= alive.size()) {
            endVote();
        }
    }

    // ==================== 공통 ====================

    private void kill(ClientHandler user) {
        alive.remove(user);
        votes.remove(user);
        broadcastAlive();
    }



    // 사망 발표 뒤에 본인에게만 안내
    private void tellDead(ClientHandler user) {
        if (user != null && room.contains(user)) {
            tell(user, "[알림] 당신은 사망했습니다. 채팅·능력·투표는 할 수 없지만, 마피아 채팅을 포함한 모든 대화를 볼 수 있습니다.");
        }
    }

    // 승패 판정. 끝났으면 결과를 알리고 true.
    // 마피아가 모두 죽으면 시민 팀 승리, 마피아 수가 나머지 수 이상이면 마피아 팀 승리.
    private boolean checkWin() {
        int mafia = 0;
        for (ClientHandler u : alive) {
            if (roles.get(u) == Role.MAFIA) mafia++;
        }
        int others = alive.size() - mafia;

        String winner;
        if (mafia == 0) winner = "시민 팀 승리! 마피아를 모두 찾아냈습니다.";
        else if (mafia >= others) winner = "마피아 팀 승리! 마피아 수가 시민 수 이상이 되었습니다.";
        else return false;

        finished = true;
        room.broadcast(MessageProtocol.build(MessageProtocol.GAME_END, winner));
        room.notice("게임 종료 - " + winner);
        StringBuilder sb = new StringBuilder("[결과] ");
        for (int i = 0; i < participants.size(); i++) {
            ClientHandler u = participants.get(i);
            if (i > 0) sb.append(" / ");
            sb.append(u.getDisplayName()).append(": ").append(roles.get(u).getDisplayName());
            if (!alive.contains(u)) sb.append("(사망)");
        }
        room.broadcast(MessageProtocol.build(MessageProtocol.ROOM_MSG, sb.toString()));
        room.notice("방장이 다시 게임을 시작할 수 있습니다.");
        return true;
    }

    private ClientHandler findParticipant(String name) {
        for (ClientHandler u : participants) {
            if (u.getDisplayName().equals(name)) return u;
        }
        return null;
    }

    private void broadcastPhase() {
        String p = phase == Phase.NIGHT ? MessageProtocol.PHASE_NIGHT
                : phase == Phase.DAY ? MessageProtocol.PHASE_DAY : MessageProtocol.PHASE_VOTE;
        room.broadcast(MessageProtocol.build(MessageProtocol.PHASE,
                p + MessageProtocol.FIELD_SEP + day + MessageProtocol.FIELD_SEP + remaining));
    }

    private void broadcastAlive() {
        List<String> names = new ArrayList<>();
        for (ClientHandler u : alive) names.add(u.getDisplayName());
        room.broadcast(MessageProtocol.build(MessageProtocol.ALIVE, String.join(MessageProtocol.FIELD_SEP, names)));
    }


    // 한 사람에게만 보이는 알림
    private void tell(ClientHandler user, String text) {
        user.send(MessageProtocol.build(MessageProtocol.ROOM_MSG, text));
    }

    // 마피아에게만 보이는 알림 (지목 알림 등)
    private void tellMafia(String text) {
        sendToMafia(MessageProtocol.build(MessageProtocol.ROOM_MSG, text));
    }

    // 완성된 메시지를 마피아에게만 전송
    private void sendToMafia(String msg) {
        for (ClientHandler u : room.getPlayers()) {
            if (roles.get(u) == Role.MAFIA) u.send(msg);
        }
    }

    // 완성된 메시지를 마피아와 사망자에게 전송 (밤 마피아 채팅용)
    private void sendToMafiaAndDead(String msg) {
        for (ClientHandler u : room.getPlayers()) {
            if (roles.get(u) == Role.MAFIA || !alive.contains(u)) u.send(msg);
        }
    }

    // "마피아 1명, 경찰 1명, 의사 1명, 시민 1명"
    private static String roleSummary(List<Role> deck) {
        StringBuilder sb = new StringBuilder();
        for (Role r : Role.values()) {
            int n = Collections.frequency(deck, r);
            if (n == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(r.getDisplayName()).append(' ').append(n).append("명");
        }
        return sb.toString();
    }

    private static String mafiaTeamText(ClientHandler me, List<String> mafiaNames) {
        List<String> others = new ArrayList<>(mafiaNames);
        others.remove(me.getDisplayName());
        if (others.isEmpty()) return "이번 게임의 마피아는 당신 혼자입니다.";
        return "동료 마피아: " + String.join(", ", others);
    }
}
