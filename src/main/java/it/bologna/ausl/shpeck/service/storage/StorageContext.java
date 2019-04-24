package it.bologna.ausl.shpeck.service.storage;

/**
 *
 * @author spritz
 */
public class StorageContext {
    
    private StorageStrategy storageStrategy;

    public StorageContext(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }
    
    public UploadMessage store(String folderName, UploadMessage uploadMessage){
        return storageStrategy.storeMessage(folderName, uploadMessage);
    }
    
}
