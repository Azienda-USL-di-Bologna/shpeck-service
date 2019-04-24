package it.bologna.ausl.shpeck.service.storage;

import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;

/**
 *
 * @author spritz
 */
public interface StorageStrategy {
    
    public UploadMessage storeMessage(String folderName, UploadMessage uploadMessage) throws ShpeckServiceException;
    
    public void setFolderPath(String folderPath);
}
