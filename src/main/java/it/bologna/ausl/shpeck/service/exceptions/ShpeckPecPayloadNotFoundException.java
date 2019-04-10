package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author spritz
 */
public class ShpeckPecPayloadNotFoundException extends ShpeckServiceException {

    private static final long serialVersionUID = 1L;

    public ShpeckPecPayloadNotFoundException(String message) {
            super(message);
    }

    public ShpeckPecPayloadNotFoundException(String message, Throwable cause) {
            super(message, cause);	
    }
}
