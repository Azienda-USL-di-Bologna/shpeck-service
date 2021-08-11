package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class ShpeckServiceException extends Exception {
   
    private static final long serialVersionUID = 1L;
    private ErrorTypes errorType = ErrorTypes.GENERIC_ERROR;
    public static enum ErrorTypes{
        STOREGE_ERROR,
        GENERIC_ERROR
    }
    
    public ShpeckServiceException (String message){
        super(message);
    }

    public ShpeckServiceException(String message, Throwable cause) {
        super (message,cause);
    }
    
    public ShpeckServiceException (String message, ErrorTypes errorType){
        super(message);
        this.errorType = errorType;
    }

    public ShpeckServiceException(String message, Throwable cause, ErrorTypes errorType) {
        super (message,cause);
        this.errorType = errorType;
    }

    public ErrorTypes getErrorType() {
        return errorType;
    }
    
}
