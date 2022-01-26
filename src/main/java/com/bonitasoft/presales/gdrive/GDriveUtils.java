package com.bonitasoft.presales.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.DriveList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GDriveUtils {
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private NetHttpTransport transport;
  Logger logger = LoggerFactory.getLogger(this.getClass());

  private NetHttpTransport getTransport() throws GeneralSecurityException, IOException {
    if (transport == null) {
      this.transport = GoogleNetHttpTransport.newTrustedTransport();
    }
    return transport;
  }

  Drive getDriveServiceViaOAuth(
      java.io.File credentialsFilePath,
      java.io.File tokenFolderStore,
      Collection<String> scopes,
      String applicationName)
      throws IOException, GeneralSecurityException {
    Reader reader = new FileReader(credentialsFilePath);
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
    final NetHttpTransport transport = getTransport();
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(transport, JSON_FACTORY, clientSecrets, scopes)
            .setDataStoreFactory(new FileDataStoreFactory(tokenFolderStore))
            .setAccessType("offline")
            .build();
    Credential credentials =
        new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    return new Drive.Builder(transport, JSON_FACTORY, credentials)
        .setApplicationName(applicationName)
        .build();
  }

  public Drive getDriveServiceViaServiceAccount(
      java.io.File credentialsFilePath, Collection<String> scopes)
      throws IOException, GeneralSecurityException {
    final InputStream credentialsStream = new FileInputStream(credentialsFilePath);
    final ServiceAccountCredentials serviceAccountCredentials =
        ServiceAccountCredentials.fromStream(credentialsStream);
    HttpRequestInitializer requestInitializer =
        new HttpCredentialsAdapter(
            serviceAccountCredentials
                .createScoped(scopes)
                .createDelegated(serviceAccountCredentials.getClientEmail()));
    Drive service =
        new Drive.Builder(getTransport(), JSON_FACTORY, requestInitializer)
            .setApplicationName(serviceAccountCredentials.getProjectId())
            .build();

    logger.info(
        "connecting using service account [{}] project [{}]",
        serviceAccountCredentials.getClientEmail(),
        serviceAccountCredentials.getProjectId());
    return service;
  }

  List<com.google.api.services.drive.model.Drive> listDrives(Drive service) throws IOException {
    List<com.google.api.services.drive.model.Drive> drivesFound = new ArrayList<>();
    String pageToken = null;
    do {
      DriveList result =
          service
              .drives()
              .list()
              .setFields("nextPageToken, drives(id, name)")
              .setPageToken(pageToken)
              .execute();
      drivesFound.addAll(result.getDrives());
      for (com.google.api.services.drive.model.Drive drive : result.getDrives()) {
        logger.info("Found drive : {} ({}) ", drive.getName(), drive.getId());
      }
      pageToken = result.getNextPageToken();
    } while (pageToken != null);
    return drivesFound;
  }

  public File createFolder(Drive service, String driveId, String parentFolderId, String folderName)
      throws IOException {
    String parentFolder = driveId;
    if (parentFolderId != null) {
      parentFolder = parentFolderId;
    }
    File fileMetadata = new File();
    fileMetadata.setName(folderName);
    fileMetadata.setDriveId(driveId);
    fileMetadata.setParents(Collections.singletonList(parentFolder));
    fileMetadata.setMimeType("application/vnd.google-apps.folder");
    File file =
        service.files().create(fileMetadata).setSupportsAllDrives(true).setFields("id").execute();
    logger.info(
        "create folder in drive [{}] with name [{}] and id [{}]",
        driveId,
        folderName,
        file.getId());
    return file;
  }

  public File createFile(
      Drive service,
      String driveId,
      String parentFolderId,
      java.io.File fileToCreate,
      String mimeType)
      throws IOException {
    String parentFolder = driveId;
    if (parentFolderId != null) {
      parentFolder = parentFolderId;
    }
    FileContent mediaContent = new FileContent(mimeType, fileToCreate);
    File fileMetadata = new File();
    fileMetadata.setName(fileToCreate.getName());
    fileMetadata.setDriveId(driveId);
    fileMetadata.setParents(Collections.singletonList(parentFolder));
    fileMetadata.setMimeType(mimeType);
    File file =
        service
            .files()
            .create(fileMetadata, mediaContent)
            .setSupportsAllDrives(true)
            .setFields("id, webViewLink")
            .execute();
    logger.info(
        "create file in drive [{}] under folder [{}] with name [{}]",
        driveId,
        parentFolder,
        fileToCreate.getName());
    return file;
  }

  static FileList getFolderContent(Drive service, String driveId, String parentFolderId)
      throws IOException {
    final Drive.Files.List query =
        service
            .files()
            .list()
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .setCorpora("drive")
            .setDriveId(driveId)
            .setSupportsAllDrives(true)
            .setFields(
                "nextPageToken,files(id,name,mimeType,webViewLink,capabilities/canAddChildren)");
    if (parentFolderId != null) {
      query.setQ("'" + parentFolderId + "' in parents");
    }
    return query.execute();
  }

  public void deleteFolder(Drive service, String folderId) throws IOException {
    deleteFile(service, folderId);
  }

  void deleteFile(Drive service, String fileId) throws IOException {
    logger.info("delete file/folder with id [{}]", fileId);
    service.files().delete(fileId).setSupportsAllDrives(true).execute();
  }

  public static ArrayList<String> getAllScopes() {
    return new ArrayList<>(DriveScopes.all());
  }
}
