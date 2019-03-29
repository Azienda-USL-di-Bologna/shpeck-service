/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shpeck.service.exceptions;

/**
 *
 * @author Salo
 */
public class MailMessageException extends ShpeckServiceException {
    
    public MailMessageException(String message) {
        super(message);
    }
    
    public MailMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
