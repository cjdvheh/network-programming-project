package Project.Protocol;

public class ChatMessageData {


    public enum Type { CHAT, MAFIA_CHAT, NOTICE }

    private final Type type;
    private final String sender;   // NOTICE면 빈 문자열
    private final String text;

    public ChatMessageData(Type type, String sender, String text) {
        this.type = type;
        this.sender = sender;
        this.text = text;
    }

    public Type getType() {
        return type;
    }

    public String getSender() {
        return sender == null ? "" :  sender;
    }

    public String getText() {
        return text == null ? "" :  text;
    }

    public String toBody() {

        return String.join(MessageProtocol.FIELD_SEP, type.name(), getSender(), getText());
    }

    //문자열 -> 객체
    public static ChatMessageData fromBody(String body) {
        String[] list = body.split(MessageProtocol.FIELD_SEP, 3);

        if(list.length < 3) {
            return null;
        }

        try {
            return new ChatMessageData(Type.valueOf(list[0]), list[1], list[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


}
