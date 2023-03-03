package com.bonitasoft.presales.connector;

import static com.bonitasoft.presales.connector.GoogleDriveUpload.OUTPUT_CREATED_FILE_LIST;
import static com.bonitasoft.presales.connector.GoogleDriveUpload.OUTPUT_CREATED_FOLDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.api.services.drive.model.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bonitasoft.engine.api.APIAccessor;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.engine.connector.EngineExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoogleDriveUploadTest {

  private static final String SERVICE_ACCOUNT_CREDENTIALS = "/gdrive/presales-gdrive-service.json";
  public static final String TEST_FOLDER_NAME = "Test Folder Name";
  public static final String DOCUMENT_NAME = "myDocument";
  public static final String DOCUMENT_CONTENT_ID = "5678";
  public static final String DOCUMENT_CONTENT_TYPE = "plain/text";
  public static final String DOCUMENT_CONTENT_FILE_NAME = "myDocument.txt";

  public static final String DOCUMENT2_NAME = "myDocument2";
  public static final String DOCUMENT2_CONTENT_ID = "9584";
  public static final String DOCUMENT2_CONTENT_TYPE = "plain/text";
  public static final String DOCUMENT2_CONTENT_FILE_NAME = "myDocument2.txt";

  public static final long PROCESS_INSTANCE_ID = 1234L;
  GoogleDriveUpload connector;

  @Mock(lenient = true)
  private Document document;

  @Mock(lenient = true)
  private Document document2;

  @Mock(lenient = true)
  private EngineExecutionContext engineExecutionContext;

  @Mock(lenient = true)
  private APIAccessor apiAccessor;

  @Mock(lenient = true)
  private ProcessAPI processAPI;

  @BeforeEach
  public void setUp() throws Exception {
    when(document.getName()).thenReturn(DOCUMENT_NAME);
    when(document.getContentStorageId()).thenReturn(DOCUMENT_CONTENT_ID);
    when(document.getContentMimeType()).thenReturn(DOCUMENT_CONTENT_TYPE);
    when(document.getContentFileName()).thenReturn(DOCUMENT_CONTENT_FILE_NAME);

    when(document2.getName()).thenReturn(DOCUMENT2_NAME);
    when(document2.getContentStorageId()).thenReturn(DOCUMENT2_CONTENT_ID);
    when(document2.getContentMimeType()).thenReturn(DOCUMENT2_CONTENT_TYPE);
    when(document2.getContentFileName()).thenReturn(DOCUMENT2_CONTENT_FILE_NAME);

    when(apiAccessor.getProcessAPI()).thenReturn(processAPI);
    when(engineExecutionContext.getProcessInstanceId()).thenReturn(PROCESS_INSTANCE_ID);

    when(processAPI.getLastDocument(PROCESS_INSTANCE_ID, DOCUMENT_NAME)).thenReturn(document);
    when(processAPI.getDocumentContent(DOCUMENT_CONTENT_ID))
        .thenReturn("Document Content".getBytes(StandardCharsets.UTF_8));

    when(processAPI.getLastDocument(PROCESS_INSTANCE_ID, DOCUMENT2_NAME)).thenReturn(document2);
    when(processAPI.getDocumentContent(DOCUMENT2_CONTENT_ID))
        .thenReturn("Document2 Content".getBytes(StandardCharsets.UTF_8));

    connector = new GoogleDriveUpload();
    connector.setExecutionContext(engineExecutionContext);
    connector.setAPIAccessor(apiAccessor);
  }

  @Test
  void test_connector() throws ConnectorException, ConnectorValidationException, IOException {
    java.io.File credentialFile = loadCredentials();
    String credentials = Files.readString(credentialFile.toPath());
    List<String> attachments = new ArrayList<>();
    attachments.add(DOCUMENT_NAME);
    attachments.add(DOCUMENT2_NAME);
    Map<String, Object> parameters = new HashMap<>();
    parameters.put(GoogleDriveUpload.INPUT_NAME_DRIVE_ID, "0AMtuQGpj1EgnUk9PVA");
    parameters.put(GoogleDriveUpload.INPUT_NAME_ATTACHMENTS, attachments);
    parameters.put(GoogleDriveUpload.INPUT_NAME_CREDENTIALS_JSON, credentials);
    parameters.put(GoogleDriveUpload.INPUT_NAME_CREATE_FOLDER, true);
    parameters.put(GoogleDriveUpload.INPUT_NAME_FOLDER_NAME, TEST_FOLDER_NAME);

    connector.setInputParameters(parameters);
    connector.validateInputParameters();
    connector.connect();

    Map<String, Object> results = connector.execute();
    Map<String, File> created = (Map<String, File>) results.get(OUTPUT_CREATED_FILE_LIST);
    String folderId = (String) results.get(OUTPUT_CREATED_FOLDER_ID);

    assertThat(folderId).as("should have created a folder").isNotNull();
    assertThat(created).as("should have 2 documents created").hasSize(2);
    assertThat(created.get(DOCUMENT_NAME).getId()).as("Should have id on created file").isNotNull();
    assertThat(created.get(DOCUMENT_NAME).getWebViewLink()).as("Should have web view link");
    assertThat(created.get(DOCUMENT2_NAME).getId())
        .as("Should have id on created file")
        .isNotNull();
    assertThat(created.get(DOCUMENT2_NAME).getWebViewLink())
        .as("Should have web view link")
        .isNotNull();

    connector.cleanup(folderId);
  }

  private java.io.File loadCredentials() throws IOException {
    var credentialFile =
        new java.io.File(System.getProperty("user.home") + SERVICE_ACCOUNT_CREDENTIALS);
    if (!credentialFile.exists()) {
      throw new IllegalStateException(
          String.format(
              "Missing %s file for integration tests.", credentialFile.getAbsolutePath()));
    }
    return credentialFile;
  }
}
