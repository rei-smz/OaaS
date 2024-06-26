package org.hpcclab.oaas.crm.controller;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.crm.optimize.CrInstanceSpec;
import org.hpcclab.oaas.proto.OFunctionStatusUpdate;
import org.hpcclab.oaas.proto.ProtoDeploymentCondition;
import org.hpcclab.oaas.proto.ProtoOFunction;
import org.hpcclab.oaas.proto.ProtoOFunctionDeploymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hpcclab.oaas.crm.controller.K8SCrController.*;
import static org.hpcclab.oaas.crm.controller.K8sResourceUtil.makeResourceRequirements;

public class DeploymentCrFnController extends AbstractCrFnController {
  private static final Logger logger = LoggerFactory.getLogger(DeploymentCrFnController.class);

  public DeploymentCrFnController(CrtMappingConfig.FnConfig fnConfig) {
    super(fnConfig);
  }

  @Override
  public FnResourcePlan deployFunction(CrDeploymentPlan plan,
                                       ProtoOFunction function) {

    logger.debug("deploy function {} with Deployment", function.getKey());
    var instance = plan.fnInstances()
      .get(function.getKey());
    var labels = Map.of(
      CR_LABEL_KEY, parent.getTsidString(),
      CR_COMPONENT_LABEL_KEY, NAME_FUNCTION,
      CR_FN_KEY, function.getKey()
    );
    var deployConf = function.getProvision()
      .getDeployment();
    deployConf.getImage();
    if (deployConf.getImage().isEmpty())
      return FnResourcePlan.EMPTY;
    var container = new ContainerBuilder()
      .withName("fn")
      .withImage(deployConf.getImage())
      .addAllToEnv(deployConf.getEnvMap()
        .entrySet().stream().map(e -> new EnvVar(e.getKey(), e.getValue(), null))
        .toList()
      )
      .withPorts(new ContainerPortBuilder()
        .withName("http")
        .withProtocol("TCP")
        .withContainerPort(deployConf.getPort() <= 0 ? 8080:deployConf.getPort())
        .build()
      )
      .withImagePullPolicy(deployConf.getPullPolicy().isEmpty() ? null: deployConf.getPullPolicy())
      .withResources(makeResourceRequirements(instance))
      .build();
    var fnName = createName(function.getKey());
    var deploymentBuilder = new DeploymentBuilder()
      .withNewMetadata()
      .withName(fnName)
      .withLabels(labels)
      .endMetadata();
    deploymentBuilder
      .withNewSpec()
      .withReplicas(instance.minInstance() > 0? instance.minInstance(): 1)
      .withNewSelector()
      .addToMatchLabels(labels)
      .endSelector()
      .withNewTemplate()
      .withNewMetadata()
      .addToLabels(labels)
      .endMetadata()
      .withNewSpec()
      .addToContainers(container)
      .endSpec()
      .endTemplate()
      .endSpec();
    var deployment = deploymentBuilder.build();
    var svc = new ServiceBuilder()
      .withNewMetadata()
      .withName(fnName)
      .withLabels(labels)
      .endMetadata()
      .withNewSpec()
      .addToSelector(labels)
      .addToPorts(
        new ServicePortBuilder()
          .withName("http")
          .withProtocol("TCP")
          .withPort(80)
          .withTargetPort(new IntOrString(deployConf.getPort() <= 0 ? 8080:deployConf.getPort()))
          .build()
      )
      .endSpec()
      .build();
    return new FnResourcePlan(List.of(deployment, svc),
      List.of(OFunctionStatusUpdate.newBuilder()
        .setKey(function.getKey())
        .setStatus(ProtoOFunctionDeploymentStatus.newBuilder()
          .setCondition(ProtoDeploymentCondition.PROTO_DEPLOYMENT_CONDITION_RUNNING)
          .setInvocationUrl("http://" + svc.getMetadata().getName() + "." + namespace + ".svc.cluster.local")
          .build())
        .setProvision(function.getProvision())
        .build())
    );
  }

  @Override
  protected List<HasMetadata> doApplyAdjustment(String fnKey, CrInstanceSpec spec) {
    var deployment = kubernetesClient.apps()
      .deployments()
      .inNamespace(namespace)
      .withName(createName(fnKey))
      .get();
    if (deployment==null) return List.of();
    deployment.getSpec()
      .setReplicas(spec.minInstance());
    return List.of(deployment);
  }

  private String createName(String fnKey) {
    return prefix + "fn-" + fnKey.toLowerCase().replaceAll("[._]", "-")
      + "-00001";

  }

  @Override
  public List<HasMetadata> removeFunction(String fnKey) {
    List<HasMetadata> resources = Lists.mutable.empty();
    var labels = Map.of(
      CR_LABEL_KEY, parent.getTsidString(),
      CR_FN_KEY, fnKey
    );
    var deployments = kubernetesClient.apps()
      .deployments()
      .withLabels(labels)
      .list()
      .getItems();
    resources.addAll(deployments);
    var services = kubernetesClient.services().withLabels(labels)
      .list().getItems();
    resources.addAll(services);
    return resources;
  }

  @Override
  public List<HasMetadata> removeAllFunction() {
    List<HasMetadata> resourceList = Lists.mutable.empty();
    var labels = Map.of(
      CR_LABEL_KEY, parent.getTsidString(),
      CR_COMPONENT_LABEL_KEY, NAME_FUNCTION
    );
    List<Deployment> deployments = kubernetesClient.apps().deployments()
      .withLabels(labels)
      .list().getItems();
    resourceList.addAll(deployments);
    List<Service> services = kubernetesClient.services()
      .withLabels(labels)
      .list().getItems();
    resourceList.addAll(services);
    return resourceList;
  }
}
