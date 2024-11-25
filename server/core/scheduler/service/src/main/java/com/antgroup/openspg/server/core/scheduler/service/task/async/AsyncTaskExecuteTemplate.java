/*
 * Copyright 2023 OpenSPG Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 */
package com.antgroup.openspg.server.core.scheduler.service.task.async;

import com.antgroup.openspg.server.common.model.scheduler.SchedulerEnum.TaskStatus;
import com.antgroup.openspg.server.core.scheduler.model.service.SchedulerTask;
import com.antgroup.openspg.server.core.scheduler.model.task.TaskExecuteContext;
import com.antgroup.openspg.server.core.scheduler.service.metadata.SchedulerTaskService;
import com.antgroup.openspg.server.core.scheduler.service.task.TaskExecuteTemplate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

/** Job Async Task Template class. execute process functions */
public abstract class AsyncTaskExecuteTemplate extends TaskExecuteTemplate
    implements AsyncTaskExecute {

  @Autowired SchedulerTaskService schedulerTaskService;

  @Override
  public final TaskStatus execute(TaskExecuteContext context) {
    SchedulerTask task = context.getTask();
    String resource = task.getResource();
    // if resource not blank trigger getStatus
    if (StringUtils.isNotBlank(resource)) {
      context.addTraceLog("Async task submitted! Get task status. resource：%s", resource);
      return getStatus(context, resource);
    }

    context.addTraceLog("The Async task has not been submit! Go to submit");
    // if resource is blank trigger submit
    resource = submit(context);
    if (StringUtils.isBlank(resource)) {
      return TaskStatus.RUNNING;
    }

    context.addTraceLog("Async task submit successful! resource：%s", resource);
    SchedulerTask updateTask = new SchedulerTask();
    updateTask.setId(task.getId());
    updateTask.setResource(resource);
    schedulerTaskService.update(updateTask);
    return TaskStatus.RUNNING;
  }
}
