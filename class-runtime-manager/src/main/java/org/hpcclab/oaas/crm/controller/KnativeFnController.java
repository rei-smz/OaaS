package org.hpcclab.oaas.crm.controller;

import io.fabric8.knative.client.DefaultKnativeClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.knative.serving.v1.RevisionSpec;
import io.fabric8.knative.serving.v1.RevisionTemplateSpec;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceBuilder;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.exception.CrDeployException;
import org.hpcclab.oaas.crm.exception.CrUpdateException;
import org.hpcclab.oaas.crm.optimize.CrAdjustmentPlan;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.crm.optimize.CrInstanceSpec;
import org.hpcclab.oaas.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hpcclab.oaas.crm.CrmConfig.LABEL_KEY;
import static org.hpcclab.oaas.crm.controller.K8SCrController.*;
import static org.hpcclab.oaas.crm.controller.K8sResourceUtil.makeAnnotation;
import static org.hpcclab.oaas.crm.controller.K8sResourceUtil.makeResourceRequirements;

@RegisterForReflection(
  targets = {
    Service.class,
    io.fabric8.knative.serving.v1.ServiceSpec.class,
    RevisionTemplateSpec.class,
    RevisionSpec.class,
    ServiceStatus.class
  }
)
public class KnativeFnController implements FnController {
  private static final Logger logger = LoggerFactory.getLogger(KnativeFnController.class);
  KubernetesClient kubernetesClient;
  KnativeClient knativeClient;
  K8SCrController controller;
  OprcEnvironment.Config envConfig;

  public KnativeFnController(KubernetesClient kubernetesClient,
                             K8SCrController controller,
                             OprcEnvironment.Config envConfig) {
    this.kubernetesClient = kubernetesClient;
    this.knativeClient = new DefaultKnativeClient(kubernetesClient);
    this.controller = controller;
    this.envConfig = envConfig;
  }


  @Override
  public FnResourcePlan deployFunction(CrDeploymentPlan plan, ProtoOFunction function)
    throws CrDeployException {
    var instanceSpec = plan.fnInstances()
      .get(function.getKey());
    var knConf = function.getProvision()
      .getKnative().toBuilder();
    var labels = Maps.mutable.of(
      CR_LABEL_KEY, controller.getTsidString(),
      CR_COMPONENT_LABEL_KEY, "function",
      CR_FN_KEY, function.getKey()
    );

    if (!envConfig.exposeKnative()) {
      labels.put("networking.knative.dev/visibility", "cluster-local");
    }
    knConf.getImage();
    if (knConf.getImage().isEmpty())
      return FnResourcePlan.EMPTY;
    var containerBuilder = new ContainerBuilder()
      .withName("fn")
      .withImage(knConf.getImage())
      .addAllToEnv(knConf.getEnvMap()
        .entrySet().stream().map(e -> new EnvVar(e.getKey(), e.getValue(), null))
        .toList()
      )
      .withResources(makeResourceRequirements(instanceSpec));

    if (knConf.getPort() > 0) {
      containerBuilder.withPorts(new ContainerPortBuilder()
        .withProtocol("TCP")
        .withContainerPort(knConf.getPort() <= 0 ? 8080:knConf.getPort())
        .build()
      );
    }

    var fnName = createName(function.getKey());
    var annotation = makeAnnotation(Maps.mutable.empty(), knConf);
    makeAnnotation(annotation, instanceSpec);
    var serviceBuilder = new ServiceBuilder()
      .withNewMetadata()
      .withName(fnName)
      .withLabels(labels)
      .endMetadata();
    serviceBuilder.withNewSpec()
      .withNewTemplate()
      .withNewMetadata()
      .withAnnotations(annotation)
      .addToLabels(LABEL_KEY, function.getKey())
      .endMetadata()
      .withNewSpec()
      .withTimeoutSeconds(600L)
      .withContainerConcurrency(knConf.getConcurrency() > 0 ?
        (long) knConf.getConcurrency():null)
      .withContainers(containerBuilder.build())
      .endSpec()
      .endTemplate()
      .endSpec();
    return new FnResourcePlan(
      List.of(serviceBuilder.build()),
      List.of(OFunctionStatusUpdate.newBuilder()
        .setKey(function.getKey())
        .setStatus(ProtoOFunctionDeploymentStatus.newBuilder()
          .setCondition(ProtoDeploymentCondition.PROTO_DEPLOYMENT_CONDITION_DEPLOYING)
          .build())
        .setProvision(function.getProvision())
        .build())
    );
  }

  private String createName(String key) {
    return controller.prefix + "fn-" + key
      .replaceAll("[._]", "-");
  }

  @Override
  public FnResourcePlan applyAdjustment(CrAdjustmentPlan plan) {
    List<HasMetadata> resource = Lists.mutable.empty();
    for (Map.Entry<String, CrInstanceSpec> entry : plan.fnInstances().entrySet()) {
      var fnKey = entry.getKey();
      var svc = knativeClient.services()
        .inNamespace(controller.namespace)
        .withName(createName(fnKey))
        .get();
      if (svc==null) continue;
      makeAnnotation(svc.getSpec()
        .getTemplate()
        .getMetadata()
        .getAnnotations(), entry.getValue());
      resource.add(svc);
    }
    return new FnResourcePlan(
      resource,
      List.of()
    );
  }

  @Override
  public List<HasMetadata> removeFunction(String fnKey) throws CrUpdateException {
    List<HasMetadata> resources = Lists.mutable.empty();
    var labels = Map.of(
      CR_LABEL_KEY, controller.getTsidString(),
      CR_FN_KEY, fnKey
    );
    var services = knativeClient.services()
      .withLabels(labels)
      .list()
      .getItems();
    resources.addAll(services);
    return resources;
  }

  @Override
  public List<HasMetadata> removeAllFunction() {
    List<HasMetadata> resources = Lists.mutable.empty();
    var labels = Map.of(
      CR_LABEL_KEY, controller.getTsidString()
    );
    var services = knativeClient.services()
      .withLabels(labels)
      .list()
      .getItems();
    resources.addAll(services);
    logger.info("remove ksvc [{}]", resources.size());
    return resources;
  }
}
