package com.github.marschall.petclinic.load;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.HdrHistogram.Histogram;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestOperations;

import jakarta.annotation.PreDestroy;

public final class LoadGenerator {

  private final RestOperations restOperations;

  private final ExecutorService executorService;

  private final Histogram histogram;

  private final Lock histogramLock;

  public LoadGenerator(RestOperations restOperations) {
    this.restOperations = restOperations;
    this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    this.histogram = new Histogram(TimeUnit.SECONDS.toMillis(10L), 2);
    this.histogramLock = new ReentrantLock();
  }

  public void start() {
    this.submitNext();
  }

  private void submitNext() {
    this.executorService.submit(() -> {
      if (this.executorService.isShutdown()) {
        return;
      }
      this.findOwners();
      for (int i = 1; i < 4; i++) {
        if (this.executorService.isShutdown()) {
          return;
        }
        ownerPage(i);
      }
      for (int i = 1; i < 12; i++) {
        if (this.executorService.isShutdown()) {
          return;
        }
        owner(i);
      }
      if (this.executorService.isShutdown()) {
        return;
      }
      this.findVets();
      if (this.executorService.isShutdown()) {
        return;
      }
      this.submitNext();
    });
  }

  @PreDestroy
  public void close() throws InterruptedException {
    this.executorService.shutdown();
    this.executorService.awaitTermination(10L, TimeUnit.SECONDS);
  }
  
  private ResponseEntity<Void> runAndRecord(Supplier<ResponseEntity<Void>> supplier) {
    long start = System.currentTimeMillis();
    ResponseEntity<Void> result = supplier.get();
    long end = System.currentTimeMillis();
    this.histogramLock.lock();
    try {
      this.histogram.recordValue(end - start);
    } finally {
      this.histogramLock.unlock();
    }
    return result;
  }

  private void findOwners() {
    ResponseEntity<Void> responseEntity = runAndRecord(() ->
      this.restOperations.getForEntity("http://127.0.0.1:8080/owners?lastName=", Void.class));
    verify(responseEntity);
  }

  private void ownerPage(int pageIndex) {
    ResponseEntity<Void> responseEntity = runAndRecord(() ->
      this.restOperations.getForEntity("http://127.0.0.1:8080/owners?page={0}", Void.class, pageIndex));
    verify(responseEntity);
  }

  private void owner(int ownerId) {
    ResponseEntity<Void> responseEntity = runAndRecord(() ->
      this.restOperations.getForEntity("http://127.0.0.1:8080/owners/{0}", Void.class, ownerId));
    verify(responseEntity);
  }

  private void findVets() {
    ResponseEntity<Void> responseEntity = runAndRecord(() ->
      this.restOperations.getForEntity("http://127.0.0.1:8080/vets.html", Void.class));
    verify(responseEntity);
  }

  private void verify(ResponseEntity<?> responseEntity) {
    HttpStatusCode statusCode = responseEntity.getStatusCode();
    if (!statusCode.is2xxSuccessful()) {
      throw new RuntimeException("request failed with code: " + statusCode);
    }
  }

  public Histogram getHistogram() {
    return this.histogram;
  }

}
