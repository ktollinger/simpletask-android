package nl.mpcjanssen.simpletask.task.token;

public class FILE_LINK extends Token {
    public String link;
        public FILE_LINK(String value) {
            super(FILE_LINK, "link:" + value);
            link = value;
        }
    }
