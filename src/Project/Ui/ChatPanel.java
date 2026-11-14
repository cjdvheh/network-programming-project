package Project.Ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ChatPanel extends JPanel {
    private static final int BUBBLE_WIDTH = 180;
    private static final Color BG = new Color(186,206,224);
    private static final Color MINE = new Color(255, 235, 51);      // 내 말풍선 (노랑)
    private static final Color OTHER = Color.WHITE;                 // 남의 말풍선

    private final JPanel list = new JPanel();
    private final JScrollPane scroll;

    public ChatPanel() {
        super(new BorderLayout());
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(BG);

        //list를 NORTH에 붙여야 메시지가 위에서부터 쌓임
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG);
        wrapper.add(list, BorderLayout.NORTH);

        scroll = new JScrollPane(wrapper);
        scroll.getVerticalScrollBar().setUnitIncrement(16);   // 휠 스크롤 속도
        add(scroll, BorderLayout.CENTER);
    }

    public void addMessage(String name, String msg, boolean isMine)
    {
        addRow(makeBubble(name, msg, isMine), isMine ? FlowLayout.RIGHT : FlowLayout.LEFT);
    }


    // 알림: 가운데, 작은 회색 글씨
    public void addNotice(String text) {
        JLabel lbl = new JLabel(toHtml(text));
        lbl.setFont(lbl.getFont().deriveFont(11f));
        lbl.setForeground(Color.DARK_GRAY);
        addRow(lbl, FlowLayout.CENTER);
    }

    // 이름(위) + 내용(아래)을 세로로 담은 말풍선
    private JPanel makeBubble(String name, String text, boolean mine) {
        JLabel lblName = new JLabel(name);
        lblName.setFont(lblName.getFont().deriveFont(11f));
        lblName.setForeground(Color.DARK_GRAY);

        JLabel lblText = new JLabel(toHtml(text));
        lblText.setOpaque(true);                                    // 배경색이 보이게
        lblText.setBackground(mine ? MINE : OTHER);
        lblText.setBorder(new EmptyBorder(6, 10, 6, 10));           // 말풍선 안쪽 여백

        // 이름과 내용을 같은 쪽으로 정렬 (내 글이면 오른쪽 끝에 맞춤)
        float side = mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
        lblName.setAlignmentX(side);
        lblText.setAlignmentX(side);

        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);                                    // 배경은 투명하게
        bubble.add(lblName);
        bubble.add(Box.createVerticalStrut(3));                     // 이름과 내용 사이 간격
        bubble.add(lblText);
        return bubble;
    }

    private void addRow(JComponent content, int align)
    {
        JPanel row = new JPanel(new FlowLayout(align, 10, 4));
        row.setOpaque(false);
        row.add(content);

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

        list.add(row);
        list.revalidate();
        list.repaint();
        scrollToBottom();
    }

    //레이아웃이 재계산된 후 invokeLater로 미룬다.
    public void scrollToBottom()
    {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // 긴 글만 BUBBLE_WIDTH에서 줄바꿈. 짧은 글은 글자 길이만큼만
    private String toHtml(String text) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        int textWidth = getFontMetrics(getFont()).stringWidth(text);
        if (textWidth > BUBBLE_WIDTH) {
            return "<html><div style='width:" + BUBBLE_WIDTH + "px'>" + safe + "</div></html>";
        }
        return "<html>" + safe + "</html>";
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatPanel chat = new ChatPanel();
            chat.addNotice("[알림] 1일차 낮이 밝았습니다.");
            chat.addMessage("영희", "안녕하세요", false);
            chat.addMessage("철수", "반가워요", true);
            chat.addMessage("민수", "이건 아주 긴 메시지입니다. 말풍선 폭을 넘으면 자동으로 줄이 바뀌어야 합니다.", false);
            chat.addMessage("철수", "<b>태그</b>도 글자 그대로 보여야 해요", true);

            JFrame f = new JFrame("ChatPanel 미리보기");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(chat);
            f.setSize(400, 500);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
