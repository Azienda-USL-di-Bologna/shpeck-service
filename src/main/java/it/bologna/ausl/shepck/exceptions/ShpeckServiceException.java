/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.bologna.ausl.shepck.exceptions;

/**
 *
 * @author Salo
 */
public class ShpeckServiceException extends Exception {
    public ShpeckServiceException (String message){
            super(message);
    }

    public ShpeckServiceException(String message, Throwable cause) {

        super (message,cause);
    }
}
