package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author spritz
 */
public class ShpeckIllegalRecepitException extends ShpeckServiceException {
    
    private static final long serialVersionUID = 1L;

    public ShpeckIllegalRecepitException(String message) {
        super(message);
    }

    public ShpeckIllegalRecepitException(String message, Throwable cause) {
        super(message, cause);
    }
}
