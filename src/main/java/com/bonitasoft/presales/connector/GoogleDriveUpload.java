package com.bonitasoft.presales.connector;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.writeString;

import com.bonitasoft.presales.gdrive.GDriveUtils;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.bpm.document.DocumentNotFoundException;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

public class GoogleDriveUpload extends AbstractConnector {

  private static final Logger LOGGER = Logger.getLogger(GoogleDriveUpload.class.getName());

  static final String INPUT_NAME_DRIVE_ID = "driveID";
  static final String INPUT_NAME_FOLDER_NAME = "folderName";
  static final String INPUT_NAME_ATTACHMENTS = "attachments";
  static final String INPUT_NAME_CREDENTIALS_JSON = "credentialsJSON";
  static final String INPUT_NAME_CREATE_FOLDER = "createFolder";

  static final String OUTPUT_CREATED_FILE_LIST = "createdFileList";
  static final String OUTPUT_CREATED_FOLDER_ID = "createdFolderID";

  GDriveUtils gDriveUtils;
  Drive driveService;
  HashMap<String, File> createdFiles;
  String folderId;

  protected final java.lang.String getDriveID() {
    return (java.lang.String) getInputParameter(INPUT_NAME_DRIVE_ID);
  }

  protected final java.lang.String getFolderName() {
    return (java.lang.String) getInputParameter(INPUT_NAME_FOLDER_NAME);
  }

  protected final List getAttachments() {
    return (List) getInputParameter(INPUT_NAME_ATTACHMENTS);
  }

  protected final java.lang.String getCredentialsJSON() {
    return (java.lang.String) getInputParameter(INPUT_NAME_CREDENTIALS_JSON);
  }

  protected final java.lang.Boolean getCreateFolder() {
    return (java.lang.Boolean) getInputParameter(INPUT_NAME_CREATE_FOLDER);
  }

  protected final void setCreatedFileList(Object createdFileList) {
    setOutputParameter(OUTPUT_CREATED_FILE_LIST, createdFileList);
  }

  protected final void setOutputCreatedFolderId(String folderId) {
    setOutputParameter(OUTPUT_CREATED_FOLDER_ID, folderId);
  }

  private Document getDocument(Object attachment, ProcessAPI processAPI)
      throws ConnectorException, DocumentNotFoundException {
    if (attachment instanceof String && !((String) attachment).trim().isEmpty()) {
      String docName = (String) attachment;
      long processInstanceId = getExecutionContext().getProcessInstanceId();
      return processAPI.getLastDocument(processInstanceId, docName);
    } else if (attachment instanceof Document) {
      return (Document) attachment;
    } else {
      throw new ConnectorException(
          "Attachments must be document names or org.bonitasoft.engine.bpm.document.Document");
    }
  }

  /**
   * Perform validation on the inputs defined on the connector definition
   * (src/main/resources/connector-googledrive-upload.def) You should: - validate that mandatory
   * inputs are presents - validate that the content of the inputs is coherent with your use case
   * (e.g: validate that a date is / isn't in the past ...)
   */
  @Override
  public void validateInputParameters() throws ConnectorValidationException {
    checkMandatoryStringInput(INPUT_NAME_DRIVE_ID);
    checkMandatoryStringInput(INPUT_NAME_CREDENTIALS_JSON);
    checkMandatoryListInput(INPUT_NAME_ATTACHMENTS);
    checkMandatoryBooleanInput(INPUT_NAME_CREATE_FOLDER);
  }

  protected void checkMandatoryStringInput(String inputName) throws ConnectorValidationException {
    try {
      String value = (String) getInputParameter(inputName);
      if (value == null || value.isEmpty()) {
        throw new ConnectorValidationException(
            this, String.format("Mandatory parameter '%s' is missing.", inputName));
      }
    } catch (ClassCastException e) {
      throw new ConnectorValidationException(
          this, String.format("'%s' parameter must be a String", inputName));
    }
  }

  protected void checkMandatoryListInput(String inputName) throws ConnectorValidationException {
    Object value = getInputParameter(inputName);
    if (value == null) {
      throw new ConnectorValidationException(
          this, String.format("Mandatory parameter '%s' is missing.", inputName));
    }
    if (!(value instanceof List)) {
      throw new ConnectorValidationException(
          this, String.format("Mandatory parameter '%s' is not a list.", inputName));
    }
  }

  protected void checkMandatoryBooleanInput(String inputName) throws ConnectorValidationException {
    try {
      Boolean value = (Boolean) getInputParameter(inputName);
    } catch (ClassCastException cce) {
      throw new ConnectorValidationException(
          this, String.format("Mandatory parameter '%s' is missing.", inputName));
    }
  }

  /**
   * Core method: - Execute all the business logic of your connector using the inputs (connect to an
   * external service, compute some values ...). - Set the output of the connector execution. If
   * outputs are not set, connector fails.
   */
  @Override
  protected void executeBusinessLogic() throws ConnectorException {
    LOGGER.info(String.format("Drive ID: %s", getInputParameter(INPUT_NAME_DRIVE_ID)));
    LOGGER.info(String.format("Folder Name: %s", getInputParameter(INPUT_NAME_FOLDER_NAME)));
    try {
      if (getCreateFolder()) {
        LOGGER.info(
            String.format("Creating Folder Named %s", getInputParameter(INPUT_NAME_FOLDER_NAME)));
        File folder = gDriveUtils.createFolder(driveService, getDriveID(), null, getFolderName());
        folderId = folder.getId();
        setOutputCreatedFolderId(folderId);
        LOGGER.info(String.format("Folder ID %s created", folderId));
      }
      createdFiles = new HashMap<>();
      for (Object attachment : getAttachments()) {
        ProcessAPI processAPI = getAPIAccessor().getProcessAPI();
        Document document = getDocument(attachment, processAPI);
        LOGGER.info(String.format("Uploading file %s", document.getContentFileName()));
        java.io.File documentFile = new java.io.File(document.getContentFileName());
        byte[] documentContent = processAPI.getDocumentContent(document.getContentStorageId());
        FileUtils.writeByteArrayToFile(documentFile, documentContent);
        File file =
            gDriveUtils.createFile(
                driveService, getDriveID(), folderId, documentFile, document.getContentMimeType());
        createdFiles.put(document.getName(), file);
        LOGGER.info(String.format("File %s uploaded", document.getContentFileName()));
      }
      setCreatedFileList(createdFiles);
      LOGGER.info("Uploaded files " + createdFiles.toString());
    } catch (DocumentNotFoundException | IOException e) {
      throw new ConnectorException(e);
    }
  }

  /** [Optional] Open a connection to remote server */
  @Override
  public void connect() throws ConnectorException {
    try {
      gDriveUtils = new GDriveUtils();
      Path temp = createTempFile("credentials", ".json");
      writeString(temp, getCredentialsJSON());
      driveService =
          gDriveUtils.getDriveServiceViaServiceAccount(temp.toFile(), GDriveUtils.getAllScopes());
    } catch (IOException | GeneralSecurityException e) {
      throw new ConnectorException(e);
    }
  }

  public void cleanup(String folderId) throws IOException {
    gDriveUtils.deleteFolder(driveService, folderId);
  }
}
