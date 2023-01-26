package org.hpcclab.oaas.model.invocation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import org.hpcclab.oaas.model.object.OaasObject;

import java.util.List;
import java.util.Map;

@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QueuedInvocationResponse(
  String invId,
  String outId,
  OaasObject target,
  String fbName
) {
}
