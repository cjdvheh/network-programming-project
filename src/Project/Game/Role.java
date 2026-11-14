package Project.Game;

import java.util.ArrayList;
import java.util.List;

// 마피아 게임의 직업
public enum Role {
    MAFIA("마피아", "밤마다 시민 한 명을 골라 제거합니다. 마피아끼리는 서로를 압니다."),
    POLICE("경찰", "밤마다 한 명을 조사해 마피아인지 알아낼 수 있습니다."),
    DOCTOR("의사", "밤마다 한 명을 골라 마피아의 공격에서 살릴 수 있습니다."),
    CITIZEN("시민", "낮 토론과 투표로 마피아를 찾아내야 합니다.");

    private final String displayName;
    private final String description;

    Role(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    // 인원수에 맞는 직업 목록을 만든다. (섞기 전 상태)
    // 4~5명: 마피아 1, 경찰 1, 의사 1, 나머지 시민
    // 6~8명: 마피아 2, 경찰 1, 의사 1, 나머지 시민
    public static List<Role> distribute(int playerCount) {
        int mafiaCount = playerCount >= 6 ? 2 : 1;
        List<Role> roles = new ArrayList<>();
        for (int i = 0; i < mafiaCount; i++) roles.add(MAFIA);
        roles.add(POLICE);
        roles.add(DOCTOR);
        while (roles.size() < playerCount) roles.add(CITIZEN);
        return roles;
    }
}
