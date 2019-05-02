package it.bologna.ausl.shpeck.service.storage;

import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;

/**
 *
 * @author spritz
 */
public class StorageContext {
    
    private final StorageStrategy storageStrategy;

    public StorageContext(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }
    
    public UploadQueue store(String folderName, UploadQueue objectToUpload) throws ShpeckServiceException{
        return storageStrategy.storeMessage(folderName, objectToUpload);
    }
}
