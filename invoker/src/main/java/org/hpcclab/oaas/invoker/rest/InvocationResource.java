package org.hpcclab.oaas.invoker.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.hpcclab.oaas.invocation.handler.InvocationHandlerService;
import org.hpcclab.oaas.model.oal.OalResponse;
import org.hpcclab.oaas.model.oal.ObjectAccessLanguage;

@Path("/api/invocations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InvocationResource {

  @Inject
  InvocationHandlerService invocationHandlerService;

  @POST
  public Uni<OalResponse> invoke(ObjectAccessLanguage event) {
    return invocationHandlerService.syncInvoke(event)
      .map(ctx -> OalResponse.builder()
        .target(ctx.getMain())
        .output(ctx.getOutput())
        .fbName(ctx.getFbName())
        .async(false)
        .build());
  }
}