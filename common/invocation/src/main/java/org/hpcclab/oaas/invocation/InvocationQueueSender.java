package org.hpcclab.oaas.invocation;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.model.task.TaskContext;

import java.util.Collection;

public interface InvocationQueueSender {
  Uni<Void> send(InvocationRequest request);

  default Uni<Void> send(Collection<InvocationRequest> requests) {
    return Multi.createFrom().iterable(requests)
      .onItem().call(this::send)
      .collect().asList()
      .replaceWithVoid();
  }

  default Uni<Void> txSend(Collection<InvocationRequest> requests) {
    return Multi.createFrom().iterable(requests)
      .onItem().call(this::send)
      .collect().asList()
      .replaceWithVoid();
  }
}