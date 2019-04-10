package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class EmlHandlerException extends ShpeckServiceException{
    
    private static final long serialVersionUID = 1L;
    
    public EmlHandlerException(String message) {
        super(message);
    }
    
    public EmlHandlerException(String message, Throwable cause) {
        super(message, cause);
    }
}
