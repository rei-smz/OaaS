package org.hpcclab.oaas.pm.rpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.hpcclab.oaas.mapper.ProtoMapper;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.pm.service.PackagePublisher;
import org.hpcclab.oaas.proto.*;
import org.hpcclab.oaas.repository.ClassRepository;
import org.hpcclab.oaas.repository.FunctionRepository;

import java.time.Duration;
import java.util.List;

@GrpcService
public class DeploymentStatusUpdaterImpl implements DeploymentStatusUpdater {
  @Inject
  ClassRepository clsRepo;
  @Inject
  FunctionRepository fnRepo;
  @Inject
  ProtoMapper mapper;
  @Inject
  PackagePublisher packagePublisher;

  @Override
  public Uni<OprcResponse> updateCls(OClassStatusUpdate update) {
    var status = mapper.fromProto(update.getStatus());
    return clsRepo.async()
      .getAsync(update.getKey())
      .onItem().ifNull()
      .failWith(() -> StdOaasException.notFoundCls(update.getKey(), 404))
      .onFailure(NotFoundException.class).retry().withBackOff(Duration.ofMillis(500))
      .atMost(3)
      .flatMap(f -> clsRepo.async().computeAsync(update.getKey(), (k, cls) -> cls.setStatus(status))
      )
      .call(cls -> packagePublisher.submitNewCls(cls))
      .map(__ -> OprcResponse.newBuilder().setSuccess(true).build());
  }

  @Override
  public Uni<OprcResponse> updateFn(OFunctionStatusUpdate update) {
    var status = mapper.fromProto(update.getStatus());
    return fnRepo.async()
      .getAsync(update.getKey())
      .onItem().ifNull()
      .failWith(() -> StdOaasException.notFoundFunc(update.getKey(), 404))
      .onFailure(StdOaasException.class).retry().withBackOff(Duration.ofMillis(500))
      .atMost(3)
      .flatMap(f -> fnRepo.async().computeAsync(update.getKey(), (k, fn) -> {
          if (fn.getStatus()==null || fn.getStatus().getTs() < status.getTs())
            fn.setStatus(status);
          return fn;
        })
      )
      .call(fn -> packagePublisher.submitNewFunction(fn))
      .map(__ -> OprcResponse.newBuilder().setSuccess(true).build());
  }

  @Override
  @RunOnVirtualThread
  public Uni<OprcResponse> updateClsAll(OClassStatusUpdates request) {
    List<OClassStatusUpdate> list = request.getUpdateListList();
    var keys = list.stream().map(OClassStatusUpdate::getKey).toList();
    var clsMap = clsRepo.list(keys);
    for (OClassStatusUpdate update : list) {
      var cls = clsMap.get(update.getKey());
      if (cls==null) continue;
      cls.setStatus(mapper.fromProto(update.getStatus()));
    }
    clsRepo.persist(clsMap.values());
    packagePublisher.submitNewCls(clsMap.values().stream())
      .await().indefinitely();
    return Uni.createFrom().item(OprcResponse.newBuilder().setSuccess(true).build());
  }

  @Override
  @RunOnVirtualThread
  public Uni<OprcResponse> updateFnAll(OFunctionStatusUpdates request) {
    List<OFunctionStatusUpdate> list = request.getUpdateListList();
    var keys = list.stream().map(OFunctionStatusUpdate::getKey).toList();
    var fnMap = fnRepo.list(keys);
    for (var update : list) {
      var fn = fnMap.get(update.getKey());
      if (fn==null) continue;
      fn.setStatus(mapper.fromProto(update.getStatus()));
    }
    fnRepo.persist(fnMap.values());
    packagePublisher.submitNewFunction(fnMap.values().stream())
      .await().indefinitely();
    return Uni.createFrom().item(OprcResponse.newBuilder().setSuccess(true).build());
  }
}
