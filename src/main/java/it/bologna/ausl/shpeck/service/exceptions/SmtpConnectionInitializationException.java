/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.exceptions;

import javax.mail.NoSuchProviderException;

/**
 *
 * @author Salo
 */
public class SmtpConnectionInitializationException extends ShpeckServiceException {
    
    private static final long serialVersionUID = 1L;

    public SmtpConnectionInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SmtpConnectionInitializationException(String message) {
        super(message);
    }
    
}
