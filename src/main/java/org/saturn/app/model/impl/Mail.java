package org.saturn.app.model.impl;

public class Mail {
    public String owner;
    public String receiver;
    public String message;
    public String status;
    public Long createdDate;

    public Mail(String owner, String receiver, String message, String status, Long createdDate) {
        this.owner = owner;
        this.receiver = receiver;
        this.message = message;
        this.status = status;
        this.createdDate = createdDate;
    }
    
}
