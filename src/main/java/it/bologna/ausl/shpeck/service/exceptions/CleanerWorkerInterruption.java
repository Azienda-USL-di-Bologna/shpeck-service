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
public class CleanerWorkerInterruption extends ShpeckServiceException {

    private static final long serialVersionUID = 1L;

    public CleanerWorkerInterruption(String message) {
        super("Interruzione del ClenaerWorker: " + message);
    }

}
