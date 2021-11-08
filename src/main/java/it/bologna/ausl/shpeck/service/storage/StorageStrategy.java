package it.bologna.ausl.shpeck.service.storage;

import it.bologna.ausl.model.entities.baborg.Pec;
import it.bologna.ausl.model.entities.shpeck.UploadQueue;
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;

/**
 *
 * @author spritz
 */
public interface StorageStrategy {

    public UploadQueue storeMessage(Pec pec, String folderName, UploadQueue objectToUpload) throws ShpeckServiceException;

    public void setFolderPath(Pec pec);

    public void setAzienda(Pec pec);
}
