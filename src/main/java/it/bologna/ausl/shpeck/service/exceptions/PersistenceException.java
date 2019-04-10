package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class PersistenceException extends ShpeckServiceException {
    
    private static final long serialVersionUID = 1L;
    
    public PersistenceException(String message) {
        super(message);
    }
    
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
