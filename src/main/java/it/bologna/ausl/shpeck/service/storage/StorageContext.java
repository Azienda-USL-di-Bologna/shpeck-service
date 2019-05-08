package it.bologna.ausl.shpeck.service.storage;

import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import org.springframework.stereotype.Component;

/**
 *
 * @author spritz
 */
@Component
public class StorageContext {
    
    private StorageStrategy storageStrategy;

    public StorageContext(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }
    
    public UploadQueue store(String folderName, UploadQueue objectToUpload) throws ShpeckServiceException{
        return storageStrategy.storeMessage(folderName, objectToUpload);
    }

    public StorageStrategy getStorageStrategy() {
        return storageStrategy;
    }

    public void setStorageStrategy(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }
}
