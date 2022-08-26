package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class MailMessageException extends ShpeckServiceException {
    
    private static final long serialVersionUID = 1L;
    
    public MailMessageException(String message) {
        super(message);
    }
    
    public MailMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
