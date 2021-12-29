package org.hpcclab.oaas.taskmanager.initializer;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class InfinispanInit {
private static final Logger LOGGER = LoggerFactory.getLogger( InfinispanInit.class );

  // language=xml
  private static final String TEMPLATE_CONFIG = """
    <infinispan>
      <cache-container>
        <distributed-cache name="%s"
                           statistics="true"
                           mode="ASYNC">
          <memory storage="OFF_HEAP"/>
          <encoding>
              <key media-type="application/x-protostream"/>
              <value media-type="application/x-protostream"/>
          </encoding>
          <persistence passivation="false">
              <file-store fetch-state="true">
                <index path="index" />
                <data path="data" />
              </file-store>
          </persistence>
        </distributed-cache>
      </cache-container>
    </infinispan>
     """;
  // language=xml
  private static final String TEMPLATE_TX_CONFIG = """
    <infinispan>
      <cache-container>
        <distributed-cache name="%s"
                           statistics="true"
                           mode="SYNC">
          <memory storage="OFF_HEAP"/>
          <locking isolation="REPEATABLE_READ"/>
          <transaction mode="NON_XA" locking="PESSIMISTIC"/>
          <encoding>
              <key media-type="application/x-protostream"/>
              <value media-type="application/x-protostream"/>
          </encoding>
          <persistence passivation="false">
              <file-store fetch-state="true">
                <index path="index" />
                <data path="data" />
              </file-store>
          </persistence>
        </distributed-cache>
      </cache-container>
    </infinispan>
     """;
  @Inject
  RemoteCacheManager remoteCacheManager;

  public void setup() {
    if (remoteCacheManager == null) {
      throw new RuntimeException("Cannot connect to infinispan cluster");
    }
    remoteCacheManager.administration().getOrCreateCache("TaskCompletion", new XMLStringConfiguration(TEMPLATE_CONFIG.formatted("TaskCompletion")));

    remoteCacheManager.administration().getOrCreateCache("TaskState", new XMLStringConfiguration(TEMPLATE_TX_CONFIG.formatted("TaskState")));

  }
}