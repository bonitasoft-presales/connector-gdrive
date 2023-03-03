package com.bonitasoft.presales.gdrive;

import static com.bonitasoft.presales.gdrive.GDriveUtils.getAllScopes;
import static com.bonitasoft.presales.gdrive.GDriveUtils.getFolderContent;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.hamcrest.Matchers.equalTo;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GDriveUtilsTest {

  public static final String SHARED_DRIVE_ID = "0AMtuQGpj1EgnUk9PVA";
  public static final String SERVICE_ACCOUNT_CREDENTIALS = "/gdrive/presales-gdrive-service.json";
  public static final int MAX_ITERATION = 20;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  @Test
  void shouldListDrivesUsingServiceAccount() throws IOException, GeneralSecurityException {
    GDriveUtils driveUtils = new GDriveUtils();
    var credentialFile = loadCredentials();
    Drive driveService =
        driveUtils.getDriveServiceViaServiceAccount(credentialFile, getAllScopes());
    List<com.google.api.services.drive.model.Drive> drives = driveUtils.listDrives(driveService);

    assertThat(drives).as("shoul not be empty").isNotEmpty();
    assertThat(drives.get(0).getName()).as("should retrieve shared drive").isEqualTo("Showroom");
  }

  @Test
  void shouldCreateFileUnderSharedFolderUsingServiceAccount() throws Exception {
    GDriveUtils driveUtils = new GDriveUtils();
    java.io.File credentialFile = loadCredentials();
    Drive driveService =
        driveUtils.getDriveServiceViaServiceAccount(credentialFile, getAllScopes());

    String parentFolder = "test_folder_" + UUID.randomUUID();
    File folder = driveUtils.createFolder(driveService, SHARED_DRIVE_ID, null, parentFolder);
    java.io.File newFile = Files.createTempFile("test", ".txt").toFile();
    Files.writeString(newFile.toPath(), "Hello world !");

    File createdFile =
        driveUtils.createFile(driveService, SHARED_DRIVE_ID, folder.getId(), newFile, "plain/text");
    logger.info("created file name:[{}] - id:[{}]", newFile.getName(), createdFile.getId());
    logger.info("webViewLink:[{}]", createdFile.getWebViewLink());

    with()
        .pollInterval(1, SECONDS)
        .and()
        .with()
        .pollDelay(1, SECONDS)
        .await("file created")
        .atMost(20, SECONDS)
        .until(
            isFolderContainingFile(
                driveService, SHARED_DRIVE_ID, folder.getId(), newFile.getName()),
            equalTo(newFile.getName()));
    List<File> folderFiles =
        getFolderContent(driveService, SHARED_DRIVE_ID, folder.getId()).getFiles();
    assertThat(folder.getId()).as("should create folder").isNotNull();
    assertThat(folderFiles).as("should have a file created").hasSize(1);
    File listedFile = folderFiles.get(0);
    assertThat(listedFile.getName()).as("should have a file created").isEqualTo(newFile.getName());
    assertThat(listedFile.getId()).as("should have an id").isEqualTo(createdFile.getId());
    assertThat(listedFile.getMimeType()).as("should have a mime type").isEqualTo("plain/text");

    driveUtils.deleteFolder(driveService, folder.getId());
  }

  @Test
  void shouldCreateFileUnderRootDriveFolder() throws Exception {
    GDriveUtils driveUtils = new GDriveUtils();
    java.io.File credentialFile = loadCredentials();
    Drive driveService =
        driveUtils.getDriveServiceViaServiceAccount(credentialFile, getAllScopes());

    java.io.File newFile = Files.createTempFile("test", ".txt").toFile();
    Files.writeString(newFile.toPath(), "Hello world !");

    File createdFile =
        driveUtils.createFile(
            driveService, SHARED_DRIVE_ID, SHARED_DRIVE_ID, newFile, "plain/text");
    logger.info("created file name:[{}] - id:[{}]", newFile.getName(), createdFile.getId());
    logger.info("webViewLink:[{}]", createdFile.getWebViewLink());

    with()
        .pollInterval(1, SECONDS)
        .and()
        .with()
        .pollDelay(1, SECONDS)
        .await("file created")
        .atMost(20, SECONDS)
        .until(
            isFolderContainingFile(
                driveService, SHARED_DRIVE_ID, SHARED_DRIVE_ID, newFile.getName()),
            equalTo(newFile.getName()));

    FileList fileList = getFolderContent(driveService, SHARED_DRIVE_ID, SHARED_DRIVE_ID);
    logFileList(driveService, SHARED_DRIVE_ID);
    List<File> f =
        fileList.getFiles().stream()
            .filter(it -> it.getName().equals(newFile.getName()))
            .collect(Collectors.toList());
    File expected = f.get(0);
    assertThat(expected.getName()).as("should have a file created").isEqualTo(newFile.getName());
    assertThat(expected.getId()).as("should have an id").isEqualTo(createdFile.getId());
    assertThat(expected.getMimeType()).as("should have a mime type").isEqualTo("plain/text");

    driveUtils.deleteFile(driveService, createdFile.getId());
    logFileList(driveService, SHARED_DRIVE_ID);
  }

  @Test
  void shouldCreateFolderUsingServiceAccount() throws Exception {
    GDriveUtils driveUtils = new GDriveUtils();
    java.io.File credentialFile = loadCredentials();
    Drive driveService =
        driveUtils.getDriveServiceViaServiceAccount(credentialFile, getAllScopes());
    String newFolderName = "test_folder_" + UUID.randomUUID();
    File newFolder =
        driveUtils.createFolder(driveService, SHARED_DRIVE_ID, SHARED_DRIVE_ID, newFolderName);
    logger.info("created {} - [{}]", newFolderName, newFolder.getId());
    with()
        .pollInterval(1, SECONDS)
        .and()
        .with()
        .pollDelay(1, SECONDS)
        .await("folder created")
        .atMost(20, SECONDS)
        .until(
            isFolderContainingFile(driveService, SHARED_DRIVE_ID, SHARED_DRIVE_ID, newFolderName),
            equalTo(newFolderName));

    FileList files = getFolderContent(driveService, SHARED_DRIVE_ID, SHARED_DRIVE_ID);
    assertThat(newFolder.getId()).as("should have a folder created").isNotNull();
    List<File> all =
        files.getFiles().stream()
            .filter(it -> it.getName().equals(newFolderName))
            .collect(Collectors.toList());
    File expected = all.get(0);
    assertThat(expected.getName()).as("should have a folder created").isEqualTo(newFolderName);
    assertThat(expected.getId()).as("should have a folder created").isEqualTo(newFolder.getId());
    assertThat(expected.getMimeType())
        .as("should have a folder mime type")
        .isEqualTo("application/vnd.google-apps.folder");

    driveUtils.deleteFolder(driveService, newFolder.getId());
    logFileList(driveService, SHARED_DRIVE_ID);
  }

  void logFileList(Drive drive, String parentFolder) throws IOException {
    FileList result = getFolderContent(drive, SHARED_DRIVE_ID, parentFolder);
    logger.info("folder content");
    for (File file : result.getFiles()) {
      logger.info(
          "file:{} id:{} mimeType:{} can add children:{}",
          file.getName(),
          file.getId(),
          file.getMimeType(),
          file.getCapabilities().getCanAddChildren());
    }
  }

  private Callable<String> isFolderContainingFile(
      Drive driveService, String driveId, String parentFolderId, String expectedFileName) {
    return () -> getFileInFolder(driveService, driveId, parentFolderId, expectedFileName);
  }

  private String getFileInFolder(
      Drive driveService, String driveId, String parentFolderId, String expectedFileName)
      throws IOException {
    final List<File> files = getFolderContent(driveService, driveId, parentFolderId).getFiles();
    List<File> all =
        files.stream()
            .filter(it -> it.getName().equals(expectedFileName))
            .collect(Collectors.toList());
    logger.info("current folder size:[{}]", files.size());
    if (all.size() == 1) {
      return all.get(0).getName();
    }
    return null;
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
