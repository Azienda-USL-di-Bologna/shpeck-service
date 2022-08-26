package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class ProviderConnectionManagerException extends ShpeckServiceException {
    
    private static final long serialVersionUID = 1L;
    
    public ProviderConnectionManagerException (String message){
            super(message);
    }

    public ProviderConnectionManagerException(String message, Throwable cause) {
        super (message,cause);
    }
}
