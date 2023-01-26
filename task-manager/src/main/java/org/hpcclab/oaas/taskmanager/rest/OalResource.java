package org.hpcclab.oaas.taskmanager.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.smallrye.mutiny.Uni;
import org.hpcclab.oaas.invocation.ContentUrlGenerator;
import org.hpcclab.oaas.model.Views;
import org.hpcclab.oaas.model.data.AccessLevel;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.model.oal.ObjectAccessLanguage;
import org.hpcclab.oaas.model.object.OaasObject;
import org.hpcclab.oaas.model.task.TaskStatus;
import org.hpcclab.oaas.repository.ObjectRepository;
import org.hpcclab.oaas.taskmanager.TaskManagerConfig;
import org.hpcclab.oaas.taskmanager.service.InvocationHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

@Path("/oal/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OalResource {
  private static final Logger LOGGER = LoggerFactory.getLogger(OalResource.class);
  @Inject
  ObjectRepository objectRepo;
  @Inject
  ContentUrlGenerator contentUrlGenerator;
  @Inject
  TaskManagerConfig config;

  @Inject
  InvocationHandlerService invocationHandlerService;

  @POST
  @JsonView(Views.Public.class)
  public Uni<OalResponse> getObjectWithPost(ObjectAccessLanguage oal,
                                            @QueryParam("async") Boolean async,
                                            @QueryParam("timeout") Integer timeout) {
    if (oal==null)
      return Uni.createFrom().failure(BadRequestException::new);
    if (oal.getFunctionName()!=null) {
      return selectAndInvoke(oal, async);
    } else {
      return objectRepo.getAsync(oal.getTarget())
        .onItem().ifNull()
        .failWith(() -> StdOaasException.notFoundObject(oal.getTarget(), 404))
        .map(obj -> OalResponse.builder()
          .target(obj)
          .build());
    }
  }

  @GET
  @Path("{oal}")
  @JsonView(Views.Public.class)
  public Uni<OalResponse> getObject(@PathParam("oal") String oal,
                                    @QueryParam("async") Boolean async,
                                    @QueryParam("timeout") Integer timeout) {
    var oaeObj = ObjectAccessLanguage.parse(oal);
    LOGGER.debug("Receive OAE getObject '{}'", oaeObj);
    return getObjectWithPost(oaeObj, async, timeout);
  }

  @POST
  @Path("-/{filePath:.*}")
  @JsonView(Views.Public.class)
  public Uni<Response> execAndGetContentPost(@PathParam("filePath") String filePath,
                                             @QueryParam("async") Boolean async,
                                             @QueryParam("timeout") Integer timeout,
                                             ObjectAccessLanguage oal) {
    if (oal==null)
      return Uni.createFrom().failure(BadRequestException::new);
    if (oal.getFunctionName()!=null) {
      return selectAndInvoke(oal, async)
        .map(res -> createResponse(res, filePath));
    } else {
      return objectRepo.getAsync(oal.getTarget())
        .onItem().ifNull()
        .failWith(() -> StdOaasException.notFoundObject(oal.getTarget(), 404))
        .flatMap(obj -> {
          if (obj.isReadyToUsed()) {
            return Uni.createFrom().item(createResponse(obj, filePath));
          }
          if (obj.getStatus().getTaskStatus().isFailed()) {
            return Uni.createFrom().item(createResponse(obj, filePath));
          }
          if (!obj.getStatus().getTaskStatus().isSubmitted()) {
            return invocationHandlerService.awaitCompletion(obj, timeout)
              .map(newObj -> createResponse(newObj, filePath));
          }
          return Uni.createFrom().item(createResponse(obj, filePath));
        });
    }
  }


  @GET
  @JsonView(Views.Public.class)
  @Path("{oal}/{filePath:.*}")
  public Uni<Response> execAndGetContent(@PathParam("oal") String oal,
                                         @PathParam("filePath") String filePath,
                                         @QueryParam("async") Boolean async,
                                         @QueryParam("timeout") Integer timeout) {
    var oalObj = ObjectAccessLanguage.parse(oal);
    LOGGER.debug("Receive OAL getContent '{}' '{}'", oalObj, filePath);
    return execAndGetContentPost(filePath, async, timeout, oalObj);
  }

  public Uni<OalResponse> selectAndInvoke(ObjectAccessLanguage oal, Boolean async) {
    if (async==null ? !config.defaultAwaitCompletion():async) {
      return invocationHandlerService.syncInvoke(oal)
        .map(ctx -> OalResponse.builder()
          .target(ctx.getMain())
          .output(ctx.getOutput())
          .fbName(ctx.getFbName())
          .build());
    } else {
      return invocationHandlerService.asyncInvoke(oal);
    }
  }

  public Response createResponse(OalResponse oalResponse,
                                 String filePath) {
    return createResponse(
      oalResponse.output()!=null ? oalResponse.output():oalResponse.target(),
      filePath, HttpResponseStatus.SEE_OTHER.code()
    );
  }


  public Response createResponse(OaasObject object,
                                 String filePath) {
    return createResponse(object, filePath, HttpResponseStatus.SEE_OTHER.code());
  }

  public Response createResponse(OaasObject object,
                                 String filePath,
                                 int redirectCode) {
    if (object==null) return Response.status(404).build();
    if (object.getOrigin().getParentId()!=null) {
      var status = object.getStatus();
      if (status==null) {
        return Response.status(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).build();
      }
      var ts = status.getTaskStatus();
      if (ts==TaskStatus.DOING) {
        return Response.status(HttpResponseStatus.GATEWAY_TIMEOUT.code())
          .build();
      }
      if (ts.isFailed()) {
        return Response.status(HttpResponseStatus.FAILED_DEPENDENCY.code()).build();
      }
    }
    var oUrl = object.getState().getOverrideUrls();
    if (oUrl!=null && oUrl.containsKey(filePath))
      return Response.status(redirectCode)
        .location(URI.create(oUrl.get(filePath)))
        .build();
    var fileUrl = contentUrlGenerator.generateUrl(object, filePath, AccessLevel.UNIDENTIFIED);
    return Response.status(redirectCode)
      .location(URI.create(fileUrl))
      .build();
  }
}
