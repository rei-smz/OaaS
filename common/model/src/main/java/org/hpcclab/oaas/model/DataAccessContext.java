package org.hpcclab.oaas.model;

import java.util.List;
import java.util.UUID;

public class DataAccessContext {
  UUID mainId;
  String mainCls;
  UUID outId;
  String outCls;
  List<UUID> inputIds;
  List<String> inputCls;

  public String getCls(UUID id) {
    if (mainId.equals(id)) return mainCls;
    if (outId.equals(id)) return outCls;
    var i = inputIds.indexOf(id);
    if (i < 0) return null;
    return inputCls.get(i);
  }
}
