package com.github.marschall.petclinic.load;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import jakarta.annotation.PreDestroy;

public final class LoadGenerator {

  private final RestOperations restOperations;

  private final ExecutorService executorService;

  public LoadGenerator(RestOperations restOperations) {
    this.restOperations = restOperations;
    this.executorService = Executors.newVirtualThreadPerTaskExecutor();
  }

  public void start() {
    this.submitNext();
  }

  private void submitNext() {
    this.executorService.submit(() -> {
      this.findOwners();
      for (int i = 1; i < 4; i++) {
        ownerPage(i);
      }
      for (int i = 1; i < 12; i++) {
        owner(i);
      }
      this.findVets();
      this.submitNext();
    });
  }

  @PreDestroy
  public void close() {
    this.executorService.shutdownNow();
  }

  private void findOwners() {
    ResponseEntity<Void> responseEntity = this.restOperations.getForEntity("http://127.0.0.1:8080/owners?lastName=", Void.class);
    verify(responseEntity);
  }

  private void ownerPage(int pageIndex) {
    ResponseEntity<Void> responseEntity = this.restOperations.getForEntity("http://127.0.0.1:8080/owners?page={0}", Void.class, pageIndex);
    verify(responseEntity);
  }

  private void owner(int ownerId) {
    ResponseEntity<Void> responseEntity = this.restOperations.getForEntity("http://127.0.0.1:8080/owners/{0}", Void.class, ownerId);
    verify(responseEntity);
  }

  private void findVets() {
    ResponseEntity<Void> responseEntity = this.restOperations.getForEntity("http://127.0.0.1:8080/vets.html", Void.class);
    verify(responseEntity);
  }

  private void verify(ResponseEntity<?> responseEntity) {
    HttpStatusCode statusCode = responseEntity.getStatusCode();
    if (!statusCode.is2xxSuccessful()) {
      throw new RuntimeException("request failed with code: " + statusCode);
    }
  }

}
