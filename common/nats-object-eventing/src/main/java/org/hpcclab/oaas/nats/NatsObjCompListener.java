package org.hpcclab.oaas.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PreDestroy;
import org.hpcclab.oaas.model.exception.InvocationException;
import org.hpcclab.oaas.repository.event.ObjectCompletionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class NatsObjCompListener implements ObjectCompletionListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(NatsObjCompListener.class);

  private static final long DEFAULT_TIMEOUT = 10 * 60 * 1000;
  Connection nc;
  private final ThreadLocal<Dispatcher> localDispatcher;


  public NatsObjCompListener(Connection nc) {
    this.nc = nc;
    localDispatcher = ThreadLocal.withInitial(nc::createDispatcher);
  }

  @PreDestroy
  public void onShutdown() {
    cleanup();
  }

  @Override
  public void cleanup() {
    try {
      nc.close();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public Uni<String> wait(String id, Integer timeout) {
    var subject = "objects/" + id;
    return Uni.createFrom().<String>emitter(emitter -> {
        var dispatcher = localDispatcher.get();
        LOGGER.debug("start subscribe to {}", id);
        dispatcher.subscribe(subject, msg -> {
          LOGGER.debug("receive event from {}", id);
          emitter.complete(id);
        });
        emitter.onTermination(() -> dispatcher.unsubscribe(subject));
      })
      .ifNoItem()
      .after(Duration.ofMillis(timeout==null ? DEFAULT_TIMEOUT:timeout))
      .failWith(() -> new InvocationException("listener timeout", 504));
  }

  @Override
  public boolean healthcheck() {
    if (nc.getStatus()==Connection.Status.CONNECTED)
      return true;
    LOGGER.error("NATS client status is {}", nc.getStatus());
    return false;
  }
}
