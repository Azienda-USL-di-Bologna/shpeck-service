package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class StoreManagerExeption extends ShpeckServiceException {
    private static final long serialVersionUID = 1L;
    
    public StoreManagerExeption (String message){
            super(message);
    }

    public StoreManagerExeption(String message, Throwable cause) {
        super (message,cause);
    }
}
