package Project.Protocol;

public class MessageProtocol {
    public static final String SEP = "|";

    //클라 => 서버
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";

    //서버 => 클라
    public static final String LOGIN_OK = "LOGIN_OK";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String WAITING_USERS = "WAITING_USERS";

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
}
