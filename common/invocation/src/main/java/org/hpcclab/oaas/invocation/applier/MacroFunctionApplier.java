package org.hpcclab.oaas.invocation.applier;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oaas.invocation.ContextLoader;
import org.hpcclab.oaas.model.exception.FunctionValidationException;
import org.hpcclab.oaas.model.function.DataflowStep;
import org.hpcclab.oaas.model.function.FunctionExecContext;
import org.hpcclab.oaas.model.function.FunctionType;
import org.hpcclab.oaas.model.function.Dataflow;
import org.hpcclab.oaas.model.object.ObjectReference;
import org.hpcclab.oaas.model.object.OaasObject;
import org.hpcclab.oaas.repository.OaasObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class MacroFunctionApplier implements FunctionApplier {
  private static final Logger LOGGER = LoggerFactory.getLogger(MacroFunctionApplier.class);

  @Inject
  UnifiedFunctionRouter router;
  @Inject
  ContextLoader contextLoader;
  @Inject
  OaasObjectFactory objectFactory;

  public void validate(FunctionExecContext context) {
    if (context.getFunction().getType()!=FunctionType.MACRO)
      throw new FunctionValidationException("Function must be MACRO");
  }

  private void setupMap(FunctionExecContext ctx) {
    Map<String, OaasObject> map = new HashMap<>();
    ctx.setWorkflowMap(map);
    map.put("$self", ctx.getMain());
    for (int i = 0; i < ctx.getInputs().size(); i++) {
      map.put("$" + i, ctx.getInputs().get(i));
    }
  }

  public Uni<FunctionExecContext> apply(FunctionExecContext context) {
    validate(context);
    setupMap(context);
    var func = context.getFunction();
    return execWorkflow(context, func.getMacro())
      .map(ignored -> {
        var output = export(func.getMacro(), context);
        context.setOutput(output);
        return context;
      });
  }

  private OaasObject export(Dataflow dataflow,
                            FunctionExecContext ctx) {
    if (dataflow.getExport()!=null) {
      return ctx.getWorkflowMap()
        .get(dataflow.getExport());
    } else {
      var output = objectFactory.createOutput(ctx);
      var mem = dataflow.getExports()
        .stream()
        .map(export -> new ObjectReference()
          .setName(export.getAs())
          .setObjId(ctx.getWorkflowMap()
            .get(export.getFrom()).getId()))
        .collect(Collectors.toUnmodifiableSet());
      output.setRefs(mem);
      return output;
    }
  }

  private Uni<List<FunctionExecContext>> execWorkflow(FunctionExecContext context,
                                                      Dataflow workflow) {
    var request = context.getRequest();
    return Multi.createFrom().iterable(workflow.getSteps())
      .onItem().transformToUniAndConcatenate(step -> {
        LOGGER.trace("Execute step {}", step);
        return loadSubContext(context, step)
          .flatMap(newCtx -> router.apply(newCtx))
          .invoke(newCtx -> {
            if (newCtx.getOutput() != null
              && step.getAs() != null
              && request != null
              && request.macroIds().containsKey(step.getAs()))
              newCtx.getOutput().setId(request.macroIds().get(step.getAs()));
            context.getWorkflowMap().put(step.getAs(), newCtx.getOutput());
          });
      })
      .collect().asList()
      .invoke(ctxList -> {
        for (var ctx: ctxList) {
          context.addTaskOutput(ctx.getOutput());
        }
      });
  }

  public Uni<FunctionExecContext> loadSubContext(FunctionExecContext baseCtx,
                                               DataflowStep step) {
    var newCtx = new FunctionExecContext();
    newCtx.setParent(baseCtx);
    newCtx.setArgs(step.getArgs());
    if (step.getArgRefs()!=null && !step.getArgRefs().isEmpty()) {
      var map = new HashMap<String, String>();
      Map<String, String> baseArgs = baseCtx.getArgs();
      if (baseArgs!=null) {
        for (var entry : step.getArgRefs().entrySet()) {
          var resolveArg = baseArgs.get(entry.getValue());
          map.put(entry.getKey(), resolveArg);
        }
      }
      if (newCtx.getArgs()!=null) {
        newCtx.setArgs(Maps.mutable.ofMap(newCtx.getArgs()));
        newCtx.getArgs().putAll(map);
      } else {
        newCtx.setArgs(map);
      }
    }
    baseCtx.addSubContext(newCtx);
    return contextLoader.resolveObj(baseCtx, step.getTarget())
      .invoke(newCtx::setMain)
      .map(ignore -> contextLoader.loadClsAndFunc(newCtx, step.getFunction()))
      .chain(() -> resolveInputs(newCtx, step));
  }


  private Uni<FunctionExecContext> resolveInputs(FunctionExecContext baseCtx,
                                                 DataflowStep step) {
    List<String> inputRefs = step.getInputRefs()==null ? List.of():step.getInputRefs();
    return Multi.createFrom().iterable(inputRefs)
      .onItem().transformToUniAndConcatenate(ref -> contextLoader.resolveObj(baseCtx, ref))
      .collect().asList()
      .invoke(baseCtx::setInputs)
      .replaceWith(baseCtx);
  }


}