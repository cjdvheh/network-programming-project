package Project;

import Project.Server.MainServer;
import Project.Ui.LoginFrame;

import javax.swing.SwingUtilities;

// 개발·테스트 전용 실행기. 서버 시작 + 클라이언트 자동 로그인까지 한 번에 한다.
// 완성 후에는 삭제해도 프로젝트에 영향이 없다.
public class DevLauncher {
    public static void main(String[] args) {
        // 테스트할 클라이언트 닉네임. 개수를 바꾸려면 이 배열만 수정한다.
        String[] nicknames = {"철수", "영희"};

        SwingUtilities.invokeLater(() -> {
            MainServer server = new MainServer();
            server.setVisible(true);
            server.startServer();   // Server Start 버튼 클릭 생략

            for (String name : nicknames) {
                new LoginFrame().autoLogin(name);   // 로그인 화면 건너뛰고 대기실로
            }
        });
    }
}