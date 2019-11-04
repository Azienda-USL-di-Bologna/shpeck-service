package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class BeanCreationNotAllowedExceptionShpeck extends ShpeckServiceException {
    private static final long serialVersionUID = 1L;
    
    public BeanCreationNotAllowedExceptionShpeck (String message){
            super(message);
    }

    public BeanCreationNotAllowedExceptionShpeck(String message, Throwable cause) {
        super (message,cause);
    }
}
