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

package com.antgroup.openspg.server.biz.common;

import com.antgroup.openspg.server.api.facade.Paged;
import com.antgroup.openspg.server.api.facade.dto.common.request.ProjectCreateRequest;
import com.antgroup.openspg.server.api.facade.dto.common.request.ProjectQueryRequest;
import com.antgroup.openspg.server.common.model.project.Project;
import java.util.List;

public interface ProjectManager {

  Project create(ProjectCreateRequest request);

  Project update(ProjectCreateRequest request);

  Project queryById(Long projectId);

  Integer deleteById(Long projectId);

  List<Project> query(ProjectQueryRequest request);

  Paged<Project> queryPaged(ProjectQueryRequest request, int start, int size);

  /**
   * get GraphStore Url by project ID.
   *
   * @param projectId the unique id of project
   * @return GraphStore url
   */
  String getGraphStoreUrl(Long projectId);

  /**
   * get SearchEngine Url by project ID.
   *
   * @param projectId the unique id of project
   * @return SearchEngine url
   */
  String getSearchEngineUrl(Long projectId);
}
