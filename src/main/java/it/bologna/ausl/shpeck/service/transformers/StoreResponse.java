package it.bologna.ausl.shpeck.service.transformers;

import it.bologna.ausl.model.entities.shpeck.Message;

/**
 *
 * @author Salo
 */
public class StoreResponse {

    private String status;
    private MailMessage mailMessage;
    private Message message;
    private boolean toUpload;

    public StoreResponse(String status, MailMessage mailMessage, Message message, boolean toUpload) {
        this.status = status;
        this.mailMessage = mailMessage;
        this.message = message;
        this.toUpload = toUpload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public MailMessage getMailMessage() {
        return mailMessage;
    }

    public void setMailMessage(MailMessage mailMessage) {
        this.mailMessage = mailMessage;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public boolean isToUpload() {
        return toUpload;
    }

    public void setToUpload(boolean toUpload) {
        this.toUpload = toUpload;
    }

    @Override
    public String toString() {
        return "StoreResponse{" + "status=" + status + ", mailMessage=" + mailMessage + ", message=" + message + ", toUpload=" + toUpload + '}';
    }

}
