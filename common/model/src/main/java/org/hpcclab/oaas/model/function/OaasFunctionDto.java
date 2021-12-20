package org.hpcclab.oaas.model.function;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hpcclab.oaas.model.provision.ProvisionConfig;
import org.hpcclab.oaas.model.task.TaskConfig;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OaasFunctionDto {
  @NotBlank
  String name;

  @NotNull
  OaasFunctionType type;

  String outputCls;

  OaasFunctionValidation validation;

//  TaskConfig task;

  OaasWorkflow macro;

  ProvisionConfig provision;
}