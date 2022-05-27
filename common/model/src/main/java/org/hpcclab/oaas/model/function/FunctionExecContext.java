package org.hpcclab.oaas.model.function;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oaas.model.TaskContext;
import org.hpcclab.oaas.model.object.ObjectOrigin;
import org.hpcclab.oaas.model.cls.OaasClass;
import org.hpcclab.oaas.model.object.OaasObject;

import java.util.*;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FunctionExecContext extends TaskContext {
  @JsonIgnore
  FunctionExecContext parent;
  OaasClass mainCls;
  OaasObject entry;
  OaasClass outputCls;
  List<OaasObject> subOutputs = Lists.mutable.empty();
  OaasFunctionBinding binding;
  Map<String, String> args = Map.of();
  Map<String, OaasObject> workflowMap = Maps.mutable.empty();
  List<FunctionExecContext> subContexts = Lists.mutable.empty();

  public ObjectOrigin createOrigin() {
    var finalArgs = binding.getDefaultArgs();
    if (finalArgs == null) {
      finalArgs = args;
    }
    else if (args != null) {
      finalArgs.putAll(args);
    }

    return new ObjectOrigin(
      getMain().getId(),
      binding.getName(),
      finalArgs,
      getInputs().stream().map(OaasObject::getId)
        .toList()
    );
  }

  public OaasObject resolve(String ref) {
    return workflowMap.get(ref);
  }

  public void addTaskOutput(OaasObject object) {
    subOutputs.add(object);
    if (parent != null) {
      parent.addTaskOutput(object);
    }
  }

  public void addTaskOutput(Collection<OaasObject> objects) {
    subOutputs.addAll(objects);
    if (parent != null) {
      parent.addTaskOutput(objects);
    }
  }

  public void addSubContext(FunctionExecContext ctx) {
    subContexts.add(ctx);
    if (parent != null) {
      parent.addSubContext(ctx);
    }
  }

  public boolean contains(TaskContext taskContext) {
    var outId = getOutput().getId();
    if (taskContext.getOutput().getId().equals(outId))
      return true;
    for (FunctionExecContext subContext : subContexts) {
      var b = subContext.contains(taskContext);
      if (b) {
        return true;
      }
    }
    return false;
  }
}
