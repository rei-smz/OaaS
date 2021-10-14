package org.hpcclab.msc.object.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hpcclab.msc.object.EntityConverters;
import org.hpcclab.msc.object.entity.function.OaasFunction;
import org.hpcclab.msc.object.entity.object.OaasCompoundMember;
import org.hpcclab.msc.object.entity.object.OaasObject;
import org.hpcclab.msc.object.entity.object.OaasObjectOrigin;
import org.hpcclab.msc.object.entity.state.OaasObjectState;

import javax.persistence.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OaasObjectDto {
  UUID id;
  OaasObjectOrigin origin;
  Long originHash;
  OaasObject.ObjectType type;
  OaasObject.AccessModifier access;
  Map<String, String> labels;
  Set<OaasFunctionDto> functions = Set.of();
  OaasObjectState state;
  Set<OaasCompoundMemberDto> members;
}
