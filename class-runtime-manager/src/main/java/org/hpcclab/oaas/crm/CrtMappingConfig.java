package org.hpcclab.oaas.crm;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hpcclab.oaas.crm.optimize.CrInstanceSpec;

import java.util.Map;

@RegisterForReflection(ignoreNested=false)
public record CrtMappingConfig(
  Map<String, CrtConfig> templates
) {
  public record CrtConfig(
    String type,
    Map<String, SvcConfig> services,
    String optimizer,
    Map<String, String> optimizerConf
  ){}

  public record SvcConfig(
    String image,
    Map<String, String> env,
    String imagePullPolicy,
    CrInstanceSpec defaultSpec
  ){}
}
