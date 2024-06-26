package org.hpcclab.oaas.model.object;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hpcclab.oaas.model.Copyable;
import org.hpcclab.oaas.model.HasKey;
import org.hpcclab.oaas.model.HasRev;
import org.hpcclab.oaas.model.Views;
import org.hpcclab.oaas.model.exception.InvocationException;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

import java.io.IOException;
import java.util.Map;


@Data
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OObjectV2 implements Copyable<OObjectV2>, HasKey<String>, HasRev {
  @ProtoField(1)
  Meta meta;
  @JsonProperty("data")
  ObjectNode parsedData;
  @JsonIgnore
  @ProtoField(2)
  byte[] data;

  public OObjectV2() {
    meta = new Meta();
  }

  @ProtoFactory
  public OObjectV2(Meta meta, byte[] data) {
    this.meta = meta;
    this.data = data;
  }

  @JsonCreator
  public OObjectV2(Meta meta, ObjectNode parsedData) {
    this.meta = meta;
    this.parsedData = parsedData;
  }

  public OObjectV2(Meta meta, ObjectNode parsedData, byte[] data) {
    this.meta = meta;
    this.parsedData = parsedData;
    this.data = data;
  }

  public OObjectV2 copy() {
    return new OObjectV2(
      meta!=null ? meta.copy():null,
      parsedData.deepCopy(),
      data
    );
  }

  public ObjectNode decode(ObjectMapper mapper) {
    try {
      parsedData = mapper.readValue(data, ObjectNode.class);
    } catch (IOException e) {
      throw new InvocationException(null, e);
    }
    return parsedData;
  }

  public byte[] encode(ObjectMapper mapper) {
    try {
      if (parsedData!=null) {
        data = mapper.writeValueAsBytes(parsedData);
      } else {
        data = new byte[0];
      }
      return data;
    } catch (IOException e) {
      throw new InvocationException(null, e);
    }
  }

  @Override
  public long getRevision() {
    return meta!=null ? meta.getRevision():-1;
  }

  @Override
  public void setRevision(long revision) {
    if (meta!=null)
      meta.revision = revision;
  }

  @JsonProperty("_key")
  @JsonView(Views.Internal.class)
  public String getKey() {
    return meta!=null ? meta.id:null;
  }

  @Data
  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  @Accessors(chain = true)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Meta implements Copyable<Meta> {
    @ProtoField(1)
    String id;
    @ProtoField(value = 2, defaultValue = "-1")
    long revision = -1;
    @ProtoField(3)
    String cls;
    @ProtoField(4)
    Map<String, String> overrideUrls;
    @ProtoField(5)
    Map<String, String> verIds;
    @ProtoField(6)
    Map<String, String> refs;
    @ProtoField(value = 7, defaultValue = "-1")
    long lastOffset = -1;
    @ProtoField(8)
    String lastInv;

    @ProtoFactory
    public Meta(String id, long revision, String cls, Map<String, String> overrideUrls, Map<String, String> verIds, Map<String, String> refs, long lastOffset, String lastInv) {
      this.id = id;
      this.revision = revision;
      this.cls = cls;
      this.overrideUrls = overrideUrls;
      this.verIds = verIds;
      this.refs = refs;
      this.lastOffset = lastOffset;
      this.lastInv = lastInv;
    }

    public Meta() {
    }

    public Meta(String cls) {
      this.cls = cls;
    }

    @Override
    public Meta copy() {
      return new Meta(
        id, revision, cls,
        Map.copyOf(overrideUrls),
        Map.copyOf(verIds),
        Map.copyOf(refs),
        lastOffset,
        lastInv
      );
    }
  }

}
