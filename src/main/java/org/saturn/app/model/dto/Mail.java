package org.saturn.app.model.dto;

public class Mail {
    public String owner;
    public String receiver;
    public String message;
    public String status;

    public String isWhisper;
    public Long createdDate;

    public Mail(String owner, String receiver, String message, String status, String isWhisper, Long createdDate) {
        this.owner = owner;
        this.receiver = receiver;
        this.message = message;
        this.status = status;
        this.isWhisper = isWhisper;
        this.createdDate = createdDate;
    }
    
}
