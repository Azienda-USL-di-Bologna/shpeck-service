package it.bologna.ausl.shpeck.service.storage;

/**
 *
 * @author spritz
 */
import it.bologna.ausl.shpeck.service.exceptions.ShpeckServiceException;
import it.bologna.ausl.shpeck.service.utils.MessageBuilder;
import javax.mail.internet.MimeMessage;

public class UploadMessage {
	private String name,path,uuid;
	private MimeMessage message;
	private int messageId,configId,status;
	
	public int getMessageId() {
            return messageId;
	}

	public void setMessageId(int messageId) {
            this.messageId = messageId;
	}

	public int getConfigId() {
            return configId;
	}

	public void setConfigId(int configId) {
            this.configId = configId;
	}

	public int getStatus() {
            return status;
	}

	public void setStatus(int status) {
            this.status = status;
	}

	public UploadMessage(String message) throws ShpeckServiceException{
            this.message=MessageBuilder.buildMailMessageFromString(message);
	}
	
	public String getName() {
            return name;
	}
	public void setName(String name) {
            this.name = name;
	}
	public String getPath() {
            return path;
	}
	public void setPath(String path) {
            this.path = path;
	}
	public String getUuid() {
            return uuid;
	}
	public void setUuid(String uuid) {
            this.uuid = uuid;
	}
	public MimeMessage getMessage() {
            return message;
	}
	public void setMessage(MimeMessage message) {
            this.message = message;
	}
}

