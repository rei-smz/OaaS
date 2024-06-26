package org.hpcclab.oaas.pm.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.hpcclab.oaas.pm.service.PackagePublisher;
import org.hpcclab.oaas.model.Pagination;
import org.hpcclab.oaas.model.Views;
import org.hpcclab.oaas.model.function.OFunction;
import org.hpcclab.oaas.repository.FunctionRepository;
import org.jboss.resteasy.reactive.RestQuery;

@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/functions")
public class FunctionResource {
  @Inject
  FunctionRepository funcRepo;
  @Inject
  PackagePublisher packagePublisher;

  @GET
  @JsonView(Views.Public.class)
  public Uni<Pagination<OFunction>> list(@RestQuery Long offset,
                                         @RestQuery Integer limit,
                                         @RestQuery String sort,
                                         @RestQuery @DefaultValue("false") boolean desc) {
    if (offset==null) offset = 0L;
    if (limit==null) limit = 20;
    if (sort==null) sort = "_key";
    return funcRepo.getQueryService()
      .sortedPaginationAsync(sort, desc, offset, limit);
  }

  @GET
  @Path("{funcKey}")
  @JsonView(Views.Public.class)
  public Uni<OFunction> get(String funcKey) {
    return funcRepo.async().getAsync(funcKey)
      .onItem().ifNull().failWith(NotFoundException::new);
  }

  @DELETE
  @Path("{funcKey}")
  @JsonView(Views.Public.class)
  public Uni<OFunction> delete(String funcKey) {
    return funcRepo.async().removeAsync(funcKey)
      .onItem().ifNull().failWith(NotFoundException::new)
      .call(__ -> packagePublisher.submitDeleteFn(funcKey));
  }
}
