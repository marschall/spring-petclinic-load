package com.github.marschall.petclinic.load;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.client.RestTemplate;

public class LoadApplication {

  
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static void main(String[] args) throws IOException {
    RestTemplate restTemplate = new RestTemplate(List.of(new NullHttpMessageConverter()));
    ClientHttpRequestFactory requestFactory = createRequestFactory(10);
    restTemplate.setRequestFactory(requestFactory);
    LoadGenerator loadGenerator = new LoadGenerator(restTemplate);
    LOG.info("started");
    loadGenerator.start();
    try {
      System.in.read();
      LOG.info("shutting down");
      loadGenerator.close();
    } catch (IOException e) {
      throw e;
    }
  }

  private static ClientHttpRequestFactory createRequestFactory(int maxConnections) {
    HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
        .setDefaultSocketConfig(SocketConfig.custom()
                .setSoTimeout(Timeout.ofSeconds(10L))
                .build())
        .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
        .setConnPoolPolicy(PoolReusePolicy.LIFO)
        .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofSeconds(10L))
                .setConnectTimeout(Timeout.ofSeconds(10L))
//                .setTimeToLive(TimeValue.ofMinutes(10))
                .build())
        .setMaxConnPerRoute(maxConnections)
        .setMaxConnTotal(maxConnections)
        .build();
   
    CloseableHttpClient httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .build();
   
    return  new HttpComponentsClientHttpRequestFactory(httpClient);
  }
  
  /**
   * Null implementation of {@link HttpMessageConverter} with minimal overhead on client side.
   */
  static final class NullHttpMessageConverter implements HttpMessageConverter<Void> {

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
      return clazz == Void.class;
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
      return false;
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
      return List.of(MediaType.ALL);
    }

    @Override
    public Void read(Class<? extends Void> clazz, HttpInputMessage inputMessage) {
      return null;
    }

    @Override
    public void write(Void t, MediaType contentType, HttpOutputMessage outputMessage)
        throws IOException, HttpMessageNotWritableException {
      throw new HttpMessageNotWritableException("writing not supported");
    }
    
  }

}
