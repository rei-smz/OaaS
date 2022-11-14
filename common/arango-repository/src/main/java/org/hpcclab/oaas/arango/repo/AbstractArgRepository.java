package org.hpcclab.oaas.arango.repo;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDBException;
import com.arangodb.async.ArangoCollectionAsync;
import com.arangodb.entity.DocumentDeleteEntity;
import com.arangodb.model.*;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.mutiny.core.Vertx;
import org.eclipse.collections.impl.block.factory.Functions;
import org.hpcclab.oaas.arango.DataAccessException;
import org.hpcclab.oaas.model.Pagination;
import org.hpcclab.oaas.repository.EntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractArgRepository<V>
  implements EntityRepository<String, V> {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractArgRepository.class);

  public abstract ArangoCollection getCollection();

  public abstract ArangoCollectionAsync getAsyncCollection();

  public abstract Class<V> getValueCls();

  public abstract String extractKey(V v);

  @Override
  public V get(String key) {
    Objects.requireNonNull(key);
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("get[{}] {}", getCollection().name(), key);
    return getCollection().getDocument(key, getValueCls());
  }

  @Override
  public Uni<V> getAsync(String key) {
    Objects.requireNonNull(key);
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("getAsync[{}] {}", getCollection().name(), key);
    return createUni(() -> getAsyncCollection()
      .getDocument(key, getValueCls()));
  }

  @Override
  public Map<String, V> list(Collection<String> keys) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("list[{}] {}", getCollection().name(), keys.size());
    var multiDocument = getCollection().getDocuments(keys, getValueCls());
    return multiDocument.getDocuments()
      .stream()
      .collect(Collectors.toMap(this::extractKey, Functions.identity()));
  }

  @Override
  public Uni<Map<String, V>> listAsync(Collection<String> keys) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("listAsync[{}] {}", getCollection().name(),
        keys.size());
    return createUni(() -> getAsyncCollection().getDocuments(keys, getValueCls()))
      .map(multiDocument -> multiDocument.getDocuments()
        .stream()
        .collect(Collectors.toMap(this::extractKey, Functions.identity()))
      );
  }

  @Override
  public V remove(String key) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("remove[{}] {}", getCollection().name(), key);
    var deleteEntity = getCollection().deleteDocument(key, getValueCls(), deleteOptions());
    return deleteEntity.getOld();
  }

  @Override
  public Uni<V> removeAsync(String key) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("removeAsync[{}] {}", getCollection().name(), key);
    return createUni(() -> getAsyncCollection().deleteDocument(key, getValueCls(), deleteOptions()))
      .map(DocumentDeleteEntity::getOld);
  }

  @Override
  public V put(String key, V value) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("put[{}] {}", getCollection().name(), key);
    getCollection().insertDocument(value, createOptions());
    return value;
  }

  @Override
  public Uni<V> putAsync(String key, V value) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("putAsync[{}] {}", getCollection().name(), key);
    return createUni(() -> getAsyncCollection().insertDocument(value, createOptions()))
      .replaceWith(value);
  }

  @Override
  public Uni<V> persistAsync(V v,
                             boolean notificationEnabled) {
    var k = extractKey(v);
    return putAsync(k, v);
  }

  @Override
  public Uni<V> persistWithPreconditionAsync(V v) {
    String key = extractKey(v);
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("persistWithPreconditionAsync[{}] {}", getCollection().name(), key);
    return createUni(() -> getAsyncCollection()
      .replaceDocument(key, replaceOptions()))
      .replaceWith(v);
  }

  @Override
  public Uni<Void> persistAsync(Collection<V> collection,
                                boolean notificationEnabled) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("persistAsync(col)[{}] {}",
        getCollection().name(), collection.size());
    return createUni(() -> getAsyncCollection()
      .insertDocuments(collection, createOptions()))
      .invoke(Unchecked.consumer(mde -> {
        if (!mde.getErrors().isEmpty()) {
          throw new DataAccessException(mde.getErrors());
        }
      }))
      .replaceWithVoid();
  }

  @Override
  public Uni<Void> persistWithPreconditionAsync(Collection<V> collection) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("persistWithPreconditionAsync(col)[{}] {}",
        getCollection().name(), collection.size());

    return createUni(() -> getAsyncCollection()
      .updateDocuments(collection, new DocumentUpdateOptions()
        .ignoreRevs(false)))
      .invoke(Unchecked.consumer(entities -> {
        if (!entities.getErrors().isEmpty())
          throw new DataAccessException(entities.getErrors());
      }))
      .replaceWithVoid();
  }

  @Override
  public Uni<V> computeAsync(String key, BiFunction<String, V, V> function) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("computeAsync[{}] {}",
        getCollection().name(), key);
    var uni = createUni(() -> getAsyncCollection()
      .getDocument(key, getValueCls())
      .thenCompose(doc -> {
        var newDoc = function.apply(key, doc);
        return getAsyncCollection()
          .replaceDocument(key, newDoc, replaceOptions())
          .thenApply(__ -> newDoc);
      })
    )
      .onFailure(ArangoDBException.class)
      .retry().atMost(5);
    var ctx = Vertx.currentContext();
    if (ctx!=null)
      return uni.emitOn(ctx::runOnContext);
    return uni;
  }

  @Override
  public V compute(String key, BiFunction<String, V, V> function) {
    var retryCount = 5;
    var col = getCollection();
    ArangoDBException exception = null;
    while (retryCount > 0) {
      try {
        var doc = col.getDocument(key, getValueCls());
        var newDoc = function.apply(key, doc);
        col.replaceDocument(key, newDoc, replaceOptions());
        return newDoc;
      } catch (ArangoDBException e) {
        exception = e;
      }
      retryCount--;
    }
    throw exception;
  }

  static AqlQueryOptions queryOptions() {
    return new AqlQueryOptions();
  }

  @Override
  public Pagination<V> queryPagination(String queryString, Map<String, Object> params, long offset, int limit) {
    var cursor = getCollection().db().query(queryString, params, queryOptions().fullCount(true), getValueCls());
    try (cursor) {
      var items = cursor.asListRemaining();
      return new Pagination<>(cursor.getStats().getFullCount(), offset, limit, items);
    } catch (IOException e) {
      throw new DataAccessException(e);
    }
  }

  @Override
  public Uni<Pagination<V>> queryPaginationAsync(String queryString, Map<String, Object> params, long offset, int limit) {
    return createUni(() -> getAsyncCollection()
      .db()
      .query(queryString, params, queryOptions().fullCount(true), getValueCls())
      .thenApply(cursor -> {
        try (cursor) {
          var items = cursor.streamRemaining().toList();
          return new Pagination<>(cursor.getStats().getFullCount(), offset, limit,
            items);
        } catch (IOException e) {
          throw new DataAccessException(e);
        }
      }));
  }

  protected <T> Uni<T> createUni(Supplier<CompletionStage<T>> stage) {
    var uni = Uni.createFrom().completionStage(stage);
    var ctx = Vertx.currentContext();
    if (ctx!=null)
      return uni.emitOn(ctx::runOnContext);
    return uni;
  }

  @Override
  public Pagination<V> pagination(long offset, int limit) {
    // langauge=AQL
    var query = """
      FOR doc IN @@col
        LIMIT @off, @lim
        RETURN doc
      """;
    return queryPagination(query, Map.of("@col", getCollection().name(), "off", offset, "lim", limit), offset, limit);
  }

  @Override
  public Uni<Pagination<V>> paginationAsync(long offset, int limit) {
    var query = """
      FOR doc IN @@col
        LIMIT @off, @lim
        RETURN doc
      """;
    return queryPaginationAsync(query, Map.of("@col", getCollection().name(), "off", offset, "lim", limit), offset, limit);
  }

  @Override
  public Uni<Pagination<V>> sortedPaginationAsync(String name, boolean desc, long offset, int limit) {

    var query = """
      FOR doc IN @@col
        SORT doc.@sorted @order
        LIMIT @off, @lim
        RETURN doc
      """;
    return queryPaginationAsync(query, Map.of(
        "@col", getCollection().name(),
        "sorted", name.split("\\."),
        "off", offset,
        "lim", limit,
        "order", desc ? "ASC":"DESC"
      ),
      offset, limit);
  }

  @Override
  public List<V> query(String queryString, Map<String, Object> params) {
    var cursor = getCollection().db().query(queryString, params, queryOptions(), getValueCls());
    try (cursor) {
      return cursor.asListRemaining();
    } catch (IOException e) {
      throw new DataAccessException(e);
    }
  }


  @Override
  public Uni<List<V>> queryAsync(String queryString, Map<String, Object> params) {
    return queryAsync(queryString, getValueCls(), params);
  }


  public <T> Uni<List<T>> queryAsync(String queryString, Class<T> resultCls, Map<String, Object> params) {
    return createUni(() -> getAsyncCollection()
      .db()
      .query(queryString, params, queryOptions(), resultCls).thenApply(cursor -> {
        try (cursor) {
          return cursor.streamRemaining().toList();
        } catch (IOException e) {
          throw new DataAccessException(e);
        }
      })
    );
  }

  static DocumentReplaceOptions replaceOptions() {
    return new DocumentReplaceOptions().ignoreRevs(false);
  }

  static DocumentCreateOptions createOptions() {
    return new DocumentCreateOptions()
      .overwriteMode(OverwriteMode.replace);
  }

  static DocumentDeleteOptions deleteOptions() {
    return new DocumentDeleteOptions().returnOld(true);
  }

}
