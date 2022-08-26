package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class CannotCreateTransactionShpeck extends ShpeckServiceException {
    private static final long serialVersionUID = 1L;
    
    public CannotCreateTransactionShpeck (String message){
            super(message);
    }

    public CannotCreateTransactionShpeck(String message, Throwable cause) {
        super (message,cause);
    }
}
