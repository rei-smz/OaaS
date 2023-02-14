package org.hpcclab.oaas.invocation;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.hpcclab.oaas.model.task.TaskCompletion;
import org.hpcclab.oaas.model.task.TaskIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDecoder {
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskDecoder.class);

  private TaskDecoder() {
  }


  public static TaskCompletion tryDecode(TaskIdentity taskId,
                                         Buffer buffer) {
    var ts = System.currentTimeMillis();
    if (buffer==null) {
      return TaskCompletion.error(
        taskId,
        "Can not parse the task completion message because response body is null",
        -1,
        ts);
    }
    try {
      var completion = Json.decodeValue(buffer, TaskCompletion.class);
      if (completion!=null) {
        return completion
          .setId(taskId)
          .setCptTs(ts);
      }

    } catch (DecodeException decodeException) {
      LOGGER.info("Decode failed on {} : {}", taskId.encode(), decodeException.getMessage());
      return TaskCompletion.error(
        taskId,
        "Can not parse the task completion message. [%s]".formatted(decodeException.getMessage()),
        -1,
        ts);
    }

    return TaskCompletion.error(
      taskId,
      "Can not parse the task completion message",
      -1,
      ts);
  }

  public static TaskCompletion tryDecode(String id,
                                         Buffer buffer) {
    var taskId = TaskIdentity.decode(id);
    return tryDecode(taskId, buffer);
  }
}