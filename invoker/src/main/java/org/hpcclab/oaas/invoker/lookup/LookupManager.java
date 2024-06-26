package org.hpcclab.oaas.invoker.lookup;

import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hpcclab.oaas.model.cls.OClass;
import org.hpcclab.oaas.model.cr.CrHash;

public class LookupManager {
  ConcurrentMutableMap<String, ObjLocalResolver> map = new ConcurrentHashMap<>();
  final HashRegistry registry;

  public LookupManager(HashRegistry registry) {
    this.registry = registry;
  }

  public HashRegistry getRegistry() {
    return registry;
  }

  public ObjLocalResolver getOrInit(OClass cls) {
    return map.computeIfAbsent(
      cls.getKey(),
      k -> new ObjLocalResolver(cls.getConfig().getPartitions(), cls.getKey(), registry)
    );
  }

  public boolean isLocal(CrHash.ApiAddress address) {
    if (address == null) return false;
    return address.host().equals(registry.getLocalAdvertiseAddress());
  }
}
