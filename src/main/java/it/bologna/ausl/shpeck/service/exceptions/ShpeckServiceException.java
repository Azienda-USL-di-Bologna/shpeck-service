package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class ShpeckServiceException extends Exception {
   
    private static final long serialVersionUID = 1L;
    
    public ShpeckServiceException (String message){
            super(message);
    }

    public ShpeckServiceException(String message, Throwable cause) {
        super (message,cause);
    }
}
