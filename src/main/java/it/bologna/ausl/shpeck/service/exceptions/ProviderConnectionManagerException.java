package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class ProviderConnectionManagerException extends ShpeckServiceException {
    public ProviderConnectionManagerException (String message){
            super(message);
    }

    public ProviderConnectionManagerException(String message, Throwable cause) {
        super (message,cause);
    }
}
