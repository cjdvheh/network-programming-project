package Project.Ui;

import Project.Game.GameRule;
import Project.Protocol.MessageProtocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

// 방 만들기 입력 창 (방 제목, 비밀번호). 대기실에서 "방 생성" 버튼을 누르면 열린다.
// 사용법: String[] input = CreateRoomDialog.open(owner);  -> {제목, 비밀번호} 또는 취소 시 null
public class CreateRoomDialog extends JDialog {
    private final JTextField txtTitle = new JTextField();
    private final JPasswordField txtPassword = new JPasswordField();
    private final JButton btnOk = new JButton("만들기");
    private final JButton btnCancel = new JButton("취소");
    private String[] result = null;

    public static String[] open(Frame owner) {
        CreateRoomDialog d = new CreateRoomDialog(owner);
        d.setVisible(true);   // 모달: 창이 닫힐 때까지 여기서 기다린다
        return d.result;
    }

    // 화면만 확인하는 미리보기용 main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] r = open(null);
            System.out.println(r == null ? "취소" : "제목=" + r[0] + ", 비밀번호=" + r[1]);
            System.exit(0);
        });
    }

    private CreateRoomDialog(Frame owner) {
        super(owner, "방 만들기", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.add(new JLabel("방 제목", SwingConstants.CENTER));
        panel.add(txtTitle);
        panel.add(new JLabel("비밀번호 (선택)", SwingConstants.CENTER));
        panel.add(txtPassword);
        panel.add(btnCancel);
        panel.add(btnOk);
        setContentPane(panel);

        txtTitle.setToolTipText("1~" + GameRule.MAX_TITLE_LENGTH + "자");
        txtPassword.setToolTipText("비워두면 공개방, 최대 " + GameRule.MAX_PASSWORD_LENGTH + "자");
        getRootPane().setDefaultButton(btnOk);   // Enter = 만들기

        btnOk.addActionListener(e -> submit());
        btnCancel.addActionListener(e -> dispose());

        setSize(300, 170);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private void submit() {
        String title = txtTitle.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (title.isEmpty() || title.length() > GameRule.MAX_TITLE_LENGTH) {
            JOptionPane.showMessageDialog(this, "방 제목은 1~" + GameRule.MAX_TITLE_LENGTH + "자로 입력하세요.");
            return;
        }
        if (password.length() > GameRule.MAX_PASSWORD_LENGTH) {
            JOptionPane.showMessageDialog(this, "비밀번호는 최대 " + GameRule.MAX_PASSWORD_LENGTH + "자입니다.");
            return;
        }
        if (MessageProtocol.hasReservedChar(title) || MessageProtocol.hasReservedChar(password)) {
            JOptionPane.showMessageDialog(this, "| , ; 문자는 사용할 수 없습니다.");
            return;
        }
        result = new String[]{title, password};
        dispose();
    }
}
