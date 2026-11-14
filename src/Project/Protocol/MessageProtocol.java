package Project.Protocol;

public class MessageProtocol {
    public static final String SEP = "|";

    // 내용(body) 안에 값이 여러 개일 때 쓰는 구분자
    // 예) 방 목록 "1,초보환영,3,8,WAITING,0;2,고수만,8,8,PLAYING,1"
    public static final String FIELD_SEP = ",";   // 값 사이
    public static final String ROW_SEP = ";";     // 행(방) 사이

    //클라 => 서버
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String ROOM_CREATE = "ROOM_CREATE"; // ROOM_CREATE|방제목,비밀번호(없으면 빈칸)
    public static final String ROOM_JOIN = "ROOM_JOIN";     // ROOM_JOIN|방번호,비밀번호
    public static final String ROOM_LEAVE = "ROOM_LEAVE";   // ROOM_LEAVE  (게임방 -> 대기실)
    public static final String ROOM_CHAT = "ROOM_CHAT";     // ROOM_CHAT|채팅 내용
    public static final String GAME_START = "GAME_START";   // GAME_START  (방장만 가능)
    public static final String ACTION = "ACTION";           // ACTION|대상이름  (밤: 마피아 지목 / 의사 치료 / 경찰 조사)
    public static final String VOTE = "VOTE";               // VOTE|대상이름    (투표 시간)
    public static final String EXTEND_TIME = "EXTEND_TIME"; // EXTEND_TIME     (낮 토론 시간 연장, 1인 1회)

    //서버 => 클라
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String WAITING_USERS = "WAITING_USERS";
    public static final String ROOM_LIST = "ROOM_LIST";       // ROOM_LIST|번호,제목,현재인원,최대인원,상태,비밀(1/0);...
    public static final String ROOM_ENTER = "ROOM_ENTER";     // ROOM_ENTER|방번호,방제목  (방 생성/입장 성공)
    public static final String ROOM_FAIL = "ROOM_FAIL";       // ROOM_FAIL|사유
    public static final String ROOM_USERS = "ROOM_USERS";     // ROOM_USERS|방장,참가자1,참가자2,...  (첫 값은 방장, 이후는 방장을 포함한 전체 참가자)
    public static final String ROOM_MSG = "ROOM_MSG";         // ROOM_MSG|채팅 또는 알림 한 줄
    public static final String GAME_STARTED = "GAME_STARTED"; // GAME_STARTED|직업 구성 요약
    public static final String ROLE = "ROLE";                 // ROLE|내 직업 이름 (본인에게만 전송)
    public static final String MAFIA_TEAM = "MAFIA_TEAM";     // MAFIA_TEAM|마피아1,마피아2  (마피아에게만 전송)
    public static final String PHASE = "PHASE";               // PHASE|NIGHT/DAY/VOTE,일차,남은초
    public static final String TIME = "TIME";                 // TIME|남은초  (1초마다)
    public static final String ALIVE = "ALIVE";               // ALIVE|생존자1,생존자2,...
    public static final String GAME_END = "GAME_END";         // GAME_END|승리 팀 안내

    // 방 상태 (ROOM_LIST 안에서 사용)
    public static final String STATE_WAITING = "WAITING";
    public static final String STATE_PLAYING = "PLAYING";

    // 게임 세션 (PHASE 안에서 사용)
    public static final String PHASE_NIGHT = "NIGHT";   // 밤: 마피아·의사·경찰 능력 사용, 마피아끼리만 채팅
    public static final String PHASE_DAY = "DAY";       // 낮: 밤 결과 발표 + 토론
    public static final String PHASE_VOTE = "VOTE";     // 투표: 최다 득표자 사망

    // 클라이언트 내부에서만 쓰는 값 (서버와의 연결이 끊어졌을 때)
    public static final String DISCONNECTED = "DISCONNECTED";

    // 명령과 내용을 합쳐 전송할 문자열을 만든다.
    public static String build(String cmd, String body) {
        return cmd + SEP + body;
    }

    // 받은 문자열을 [명령, 내용] 두 조각으로 나눈다. (내용 안에 | 가 있어도 첫 번째만 구분자로 사용)
    public static String[] parse(String line) {
        String[] parts = line.split("\\|", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], ""};
        }
        return parts;
    }

    // 구분자로 쓰는 문자( | , ; )가 들어 있으면 true. 방 제목·비밀번호 검사에 사용한다.
    public static boolean hasReservedChar(String s) {
        return s.contains(SEP) || s.contains(FIELD_SEP) || s.contains(ROW_SEP);
    }
}
