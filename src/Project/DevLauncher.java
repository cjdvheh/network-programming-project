package Project;

import Project.Server.MainServer;
import Project.Ui.LoginFrame;
import Project.Ui.WaitingRoomFrame;

import javax.swing.*;


// 개발·테스트 전용 실행기. 서버 시작 + 클라이언트 자동 로그인 + 테스트 방 생성/입장까지 한 번에 한다.
// 완성 후에는 삭제해도 프로젝트에 영향이 없다.
public class DevLauncher {
    private static final String TEST_ROOM_TITLE = "테스트방";

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 실패하면 기본 모양(Metal)으로 그냥 실행된다
        }

        // 테스트할 클라이언트 닉네임. 개수를 바꾸려면 이 배열만 수정한다.
        // 게임 시작은 최소 4명(GameRule.MIN_PLAYERS)이 필요하므로 4명으로 늘렸다.
        String[] nicknames = {"철수", "영희", "민수", "지수"};

        // 세션 시간을 줄여서 빠르게 테스트하려면 아래 주석을 푼다. (기본: 밤 30초 / 토론 60초 / 투표 20초)
        // Project.Game.GameRule.NIGHT_SECONDS = 15;
        // Project.Game.GameRule.DISCUSSION_SECONDS = 20;
        // Project.Game.GameRule.VOTE_SECONDS = 15;

        SwingUtilities.invokeLater(() -> {
            MainServer server = new MainServer();
            server.setVisible(true);
            server.startServer();   // Server Start 버튼 클릭 생략

            // 첫 번째 유저가 방을 만들고(방장), 나머지는 그 방이 목록에 보이면 자동으로 입장한다.
            for (int i = 0; i < nicknames.length; i++) {
                if (i == 0) {
                    new LoginFrame().autoLogin(nicknames[i], w -> w.devCreateRoom(TEST_ROOM_TITLE));
                } else {
                    new LoginFrame().autoLogin(nicknames[i], WaitingRoomFrame::devJoinFirstRoom);
                }
            }
        });
    }
}