package org.hpcclab.msc.taskgen;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.hpcclab.msc.object.entity.task.TaskFlow;
import org.hpcclab.msc.object.model.ObjectResourceRequest;
import org.hpcclab.msc.object.model.Task;
import org.hpcclab.msc.taskgen.TaskHandler;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ResourceRequestConsumer {

  @Inject
  TaskHandler taskHandler;

  @Incoming("resource-requests")
  public Uni<TaskFlow> handle(ObjectResourceRequest request) {
    return taskHandler.handle(request);
  }

}
