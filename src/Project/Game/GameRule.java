package Project.Game;

// 서버와 클라이언트가 함께 쓰는 게임/방 규칙 값
public class GameRule {
    public static final int MIN_PLAYERS = 4;          // 게임 시작 최소 인원
    public static final int MAX_PLAYERS = 8;          // 방 최대 인원
    public static final int MAX_TITLE_LENGTH = 15;    // 방 제목 최대 글자 수
    public static final int MAX_PASSWORD_LENGTH = 10; // 방 비밀번호 최대 글자 수 (비워 두면 공개방)
    public static final int MAX_CHAT_LENGTH = 200;    // 채팅 한 줄 최대 글자 수

    // ---- 세션별 시간(초) ----
    // 서버가 이 값으로 타이머를 돌려 시간이 끝나면 자동으로 다음 세션으로 넘어간다.
    // final이 아니므로 서버 실행 전에 바꿀 수 있다. (빠른 테스트는 DevLauncher 참고)
    public static int NIGHT_SECONDS = 30;       // 밤: 마피아 지목 / 의사 치료 / 경찰 조사
    public static int DISCUSSION_SECONDS = 60;  // 낮 토론
    public static int VOTE_SECONDS = 20;        // 투표
    public static int EXTEND_SECONDS = 10;      // 낮 토론 시간 연장 (1인 1회)

    private GameRule() {
    }
}
