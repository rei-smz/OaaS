package org.hpcclab.oaas.taskgen.service;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.hpcclab.oaas.model.task.OaasTask;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("")
@RegisterRestClient(configKey = "TaskBrokerService")
public interface TaskBrokerService {

  @POST
  @ClientHeaderParam(name = "ce-specversion", value = "1.0")
  @ClientHeaderParam(name = "ce-source", value = "oaas/task-generator")
  @ClientHeaderParam(name = "ce-type", value = "oaas.task")
  void submitTask(@HeaderParam("ce-id") String id,
                  @HeaderParam("ce-function") String function,
                  @HeaderParam("ce-tasktype") String taskType,
                  OaasTask task);
}
