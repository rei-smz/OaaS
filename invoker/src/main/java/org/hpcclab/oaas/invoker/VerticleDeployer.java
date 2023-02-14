package org.hpcclab.oaas.invoker;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.mutiny.core.Vertx;
import org.hpcclab.oaas.arango.ArgRepositoryInitializer;
import org.hpcclab.oaas.invoker.verticle.VerticleFactory;
import org.hpcclab.oaas.model.function.FunctionState;
import org.hpcclab.oaas.model.function.FunctionType;
import org.hpcclab.oaas.model.function.OaasFunction;
import org.hpcclab.oaas.repository.FunctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@RegisterForReflection(
  targets = {
    OaasFunction.class
  },
  registerFullHierarchy = true
)
public class VerticleDeployer {
  private static final Logger LOGGER = LoggerFactory.getLogger(VerticleDeployer.class);
  @Inject
  ArgRepositoryInitializer initializer;
  @Inject
  FunctionRepository funcRepo;
  @Inject
  VerticleFactory<?> verticleFactory;
  @Inject
  Vertx vertx;
  @Inject
  InvokerConfig config;
  @Inject
  FunctionListener functionListener;

  final ConcurrentHashMap<String, Set<AbstractVerticle>> verticleMap = new ConcurrentHashMap<>();

  void init(@Observes StartupEvent ev) {
    initializer.setup();
    var funcPage = funcRepo.pagination(0, 1000);
    var funcList = funcPage.getItems();

    functionListener.setHandler(func -> {
      LOGGER.info("receive function[{}] update event", func.getKey());
      if (func.getState()==FunctionState.ENABLED) {
        deployVerticleIfNew(func.getKey())
          .subscribe().with(__ -> {
            },
            e -> LOGGER.error("Cannot deploy verticle for function {}", func.getKey()));
      } else if (func.getState()==FunctionState.REMOVING || func.getState()==FunctionState.DISABLED) {
        deleteVerticle(func.getKey())
          .subscribe().with(__ -> {
            },
            e -> LOGGER.error("Cannot delete verticle for function {}", func.getKey()));
      }
    });
    functionListener.start().await().indefinitely();

    Multi.createFrom().iterable(funcList)
      .filter(function -> function.getState()==FunctionState.ENABLED)
      .filter(function -> function.getType()!=FunctionType.LOGICAL)
      .onItem().transformToUniAndConcatenate(func -> deployVerticleIfNew(func.getKey()))
      .collect().asList()
      .await().indefinitely();
  }

  void cleanup(@Observes ShutdownEvent event) {
    Multi.createFrom().iterable(verticleMap.values())
      .flatMap(set -> Multi.createFrom().iterable(set))
      .call(vert -> vertx.undeploy(vert.deploymentID()))
      .collect()
      .asList()
      .replaceWithVoid()
      .await().indefinitely();
  }

  public Uni<Void> deployVerticleIfNew(String function) {
    if (verticleMap.containsKey(function) && !verticleMap.get(function).isEmpty()) {
      return Uni.createFrom().nullItem();
    }
    int size = config.numOfVerticle();
    var options = new DeploymentOptions();

    return deployVerticle(function, options, size);
  }

  protected Uni<Void> deployVerticle(String function,
                                     DeploymentOptions options,
                                     int size) {
    return vertx
      .deployVerticle(() -> {
          AbstractVerticle vert = (AbstractVerticle) verticleFactory.createVerticle(function);
          verticleMap.computeIfAbsent(function, key -> new HashSet<>())
            .add(vert);
          return vert;
        },
        options)
      .repeat().atMost(size)
      .invoke(id -> {
        if (LOGGER.isInfoEnabled()) {
          LOGGER.info("deploy verticle[id={}] for function {} successfully",
            id, function);
        }
      })
      .collect()
      .last()
      .replaceWithVoid();
  }

  public Uni<Void> deleteVerticle(String function) {
    var verticleSet = verticleMap.get(function);
    if (verticleSet!=null) {
      return Multi.createFrom().iterable(verticleSet)
        .call(vert -> vertx.undeploy(vert.deploymentID()))
        .invoke(id -> LOGGER.info("Undeploy verticle[id={}] for function {} successfully", id, function))
        .collect().last()
        .replaceWithVoid()
        .invoke(() -> verticleMap.remove(function));
    }
    return Uni.createFrom().nullItem();
  }

  public Map<String, Set<AbstractVerticle>> getVerticleIds() {
    return verticleMap;
  }
}