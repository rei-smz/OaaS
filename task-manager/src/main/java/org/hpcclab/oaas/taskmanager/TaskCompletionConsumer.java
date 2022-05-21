package org.hpcclab.oaas.taskmanager;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.hpcclab.oaas.model.task.TaskCompletion;
import org.hpcclab.oaas.repository.function.InvocationGraphExecutor;
import org.hpcclab.oaas.taskmanager.service.TaskEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

@ApplicationScoped
@Path("/api/task-completions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TaskCompletionConsumer {
  private static final Logger LOGGER = LoggerFactory.getLogger( TaskCompletionConsumer.class );

//  @Inject
//  TaskEventManager taskEventManager;
  @Inject
  InvocationGraphExecutor executor;
  //  @Remote("TaskCompletion")
//  RemoteCache<UUID, TaskCompletion> remoteCache;

  @Incoming("task-completions")
  @POST
  public Uni<Void> handle(List<TaskCompletion> taskCompletions) {
//    var map = taskCompletions.stream()
//      .collect(Collectors.toMap(tc -> UUID.fromString(tc.getId()), Function.identity()));
//    return Uni.createFrom()
//      .completionStage(remoteCache.putAllAsync(map))
//      .flatMap(ignore -> taskEventManager.submitCompletionEvent(taskCompletions));
    return Multi.createFrom().iterable(taskCompletions)
      .call(tc -> executor.complete(tc))
      .collect().last()
      .replaceWithVoid();
//    return taskEventManager.submitCompletionEvent(taskCompletions);
  }

//  @Incoming("task-completions")
//  @Blocking
//  public void handleBlocking(List<TaskCompletion> taskCompletions) {
//    var map = taskCompletions.stream()
//      .collect(Collectors.toMap(tc -> UUID.fromString(tc.getId()), Function.identity()));
//    remoteCache.putAll(map);
//    taskEventManager.submitCompletionEventBlocking(taskCompletions);
//  }
}
