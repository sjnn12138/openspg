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

package com.antgroup.openspg.reasoner.udf.impl;

import static com.antgroup.openspg.reasoner.common.Utils.javaType2KgType;

import com.antgroup.openspg.reasoner.common.exception.UdfExistsException;
import com.antgroup.openspg.reasoner.common.types.KgType;
import com.antgroup.openspg.reasoner.udf.UdfMng;
import com.antgroup.openspg.reasoner.udf.model.BaseUdaf;
import com.antgroup.openspg.reasoner.udf.model.BaseUdtf;
import com.antgroup.openspg.reasoner.udf.model.IUdfMeta;
import com.antgroup.openspg.reasoner.udf.model.RuntimeUdfMeta;
import com.antgroup.openspg.reasoner.udf.model.UdafAsUdfMeta;
import com.antgroup.openspg.reasoner.udf.model.UdafMeta;
import com.antgroup.openspg.reasoner.udf.model.UdfDefine;
import com.antgroup.openspg.reasoner.udf.model.UdfMeta;
import com.antgroup.openspg.reasoner.udf.model.UdfParameterTypeHint;
import com.antgroup.openspg.reasoner.udf.model.UdtfMeta;
import com.antgroup.openspg.reasoner.udf.utils.UdfUtils;
import com.google.common.collect.Lists;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import scala.Tuple2;

@Slf4j(topic = "userlogger")
public class UdfMngImpl implements UdfMng {

  protected final Map<UdfName, Map<String, IUdfMeta>> udfMetaMap = new HashMap<>();
  protected final Map<UdfName, Map<String, UdafMeta>> udafMetaMap = new HashMap<>();
  protected final Map<UdfName, Map<String, UdtfMeta>> udtfMetaMap = new HashMap<>();

  private static volatile UdfMngImpl instance = null;

  public static ThreadLocal<Map<String, Object>> sceneConfigMap =
      ThreadLocal.withInitial(HashMap::new);

  /** 单例 */
  public static UdfMngImpl getInstance() {
    if (null == instance) {
      synchronized (UdfMngImpl.class) {
        if (null == instance) {
          instance =
              createInstance(
                  Lists.newArrayList(KGDSL_UDF_PACKAGE_PATH),
                  Lists.newArrayList(KGDSL_UDAF_PACKAGE_PATH),
                  Lists.newArrayList(KGDSL_UDTF_PACKAGE_PATH),
                  null,
                  null,
                  null);
        }
      }
    }
    return instance;
  }

  /** 多路径 */
  public static UdfMngImpl getInstance(
      List<String> udfPackagePaths,
      List<String> udafPackagePaths,
      List<String> udtfPackagePaths,
      List<UdfMeta> udfMetaList,
      List<UdafMeta> udafMetaList,
      List<UdtfMeta> udtfMetaList) {
    if (null == instance) {
      synchronized (UdfMngImpl.class) {
        if (null == instance) {
          List<String> _udfPackagePaths = Lists.newArrayList(KGDSL_UDF_PACKAGE_PATH);
          if (CollectionUtils.isNotEmpty(udfPackagePaths)) {
            _udfPackagePaths.addAll(udfPackagePaths);
          }
          List<String> _udafPackagePaths = Lists.newArrayList(KGDSL_UDAF_PACKAGE_PATH);
          if (CollectionUtils.isNotEmpty(udafPackagePaths)) {
            _udafPackagePaths.addAll(udafPackagePaths);
          }
          List<String> _udtfPackagePaths = Lists.newArrayList(KGDSL_UDTF_PACKAGE_PATH);
          if (CollectionUtils.isNotEmpty(udtfPackagePaths)) {
            _udtfPackagePaths.addAll(udtfPackagePaths);
          }
          instance =
              createInstance(
                  _udfPackagePaths,
                  _udafPackagePaths,
                  _udtfPackagePaths,
                  udfMetaList,
                  udafMetaList,
                  udtfMetaList);
        }
      }
    }
    return instance;
  }

  private static UdfMngImpl createInstance(
      List<String> udfPackagePaths,
      List<String> udafPackagePaths,
      List<String> udtfPackagePaths,
      List<UdfMeta> udfMetaList,
      List<UdafMeta> udafMetaList,
      List<UdtfMeta> udtfMetaList) {
    UdfMngImpl udfMng = new UdfMngImpl();
    for (String packagePath : udfPackagePaths) {
      udfMng.getUdfInPath(packagePath);
    }
    for (String packagePath : udafPackagePaths) {
      udfMng.getUdafInPath(packagePath);
    }
    for (String packagePath : udtfPackagePaths) {
      udfMng.getUdtfInPath(packagePath);
    }
    if (CollectionUtils.isNotEmpty(udfMetaList)) {
      udfMetaList.forEach(udfMng::addUdfMeta);
    }
    if (CollectionUtils.isNotEmpty(udafMetaList)) {
      udafMetaList.forEach(udfMng::addUdafMeta);
    }
    if (CollectionUtils.isNotEmpty(udtfMetaList)) {
      udtfMetaList.forEach(udfMng::addUdtfMeta);
    }
    udfMng.udfCheck();
    return udfMng;
  }

  private UdfMngImpl() {}

  private static final String KGDSL_UDF_PACKAGE_PATH =
      "com.antgroup.openspg.reasoner.udf.builtin.udf";

  private void getUdfInPath(String packagePath) {
    FastClasspathScanner classpathScanner = new FastClasspathScanner(packagePath);
    classpathScanner.addClassLoader(getClass().getClassLoader());
    classpathScanner
        .matchAllStandardClasses(
            aClass -> {
              Object obj;
              try {
                obj = aClass.getConstructor().newInstance();
              } catch (InstantiationException
                  | IllegalAccessException
                  | InvocationTargetException
                  | NoSuchMethodException e) {
                throw new RuntimeException(
                    "create udf meta error, className " + aClass.getName(), e);
              }
              Method[] methods = aClass.getDeclaredMethods();
              for (Method method : methods) {
                UdfDefine udfDefine = method.getAnnotation(UdfDefine.class);
                if (null == udfDefine) {
                  continue;
                }
                String name = udfDefine.name();
                String compatibleName = udfDefine.compatibleName();
                String description = udfDefine.description();
                Parameter[] parameters = method.getParameters();
                List<KgType> paramTypeList = new ArrayList<>(parameters.length);
                for (Parameter parameter : parameters) {
                  String paramName = parameter.getParameterizedType().getTypeName();
                  paramTypeList.add(javaType2KgType(paramName));
                }
                String resultType = method.getReturnType().getTypeName();
                method.setAccessible(true);
                UdfMeta udfMeta =
                    new UdfMeta(
                        name,
                        compatibleName,
                        description,
                        udfDefine.udfType(),
                        paramTypeList,
                        javaType2KgType(resultType),
                        obj,
                        method);
                log.debug("addUdfMeta,name=" + name + ",paramTypeList=" + paramTypeList);
                addUdfMeta(udfMeta);
              }
            })
        .scan();
  }

  private static final String KGDSL_UDAF_PACKAGE_PATH =
      "com.antgroup.openspg.reasoner.udf.builtin.udaf";

  private void getUdafInPath(String packagePath) {
    FastClasspathScanner classpathScanner = new FastClasspathScanner(packagePath);
    classpathScanner.addClassLoader(getClass().getClassLoader());
    classpathScanner
        .matchClassesImplementing(
            BaseUdaf.class,
            aClass -> {
              UdfDefine udfDefine = aClass.getAnnotation(UdfDefine.class);
              if (null == udfDefine) {
                return;
              }
              UdafAsUdfMeta udafMeta =
                  new UdafAsUdfMeta(
                      udfDefine.name(),
                      udfDefine.compatibleName(),
                      udfDefine.description(),
                      udfDefine.udfType(),
                      aClass);
              addUdafMeta(udafMeta);
              addUdfMeta(udafMeta);
              log.debug("addUdafMeta,name=" + udfDefine.name());
            })
        .scan();
  }

  private static final String KGDSL_UDTF_PACKAGE_PATH =
      "com.antgroup.openspg.reasoner.udf.builtin.udtf";

  private void getUdtfInPath(String packagePath) {
    FastClasspathScanner classpathScanner = new FastClasspathScanner(packagePath);
    classpathScanner.addClassLoader(getClass().getClassLoader());
    classpathScanner
        .matchClassesWithAnnotation(
            UdfDefine.class,
            aClass -> {
              if (!BaseUdtf.class.isAssignableFrom(aClass)) {
                return;
              }
              Class<? extends BaseUdtf> udtfBaseClass = (Class<? extends BaseUdtf>) aClass;
              UdfDefine udfDefine = aClass.getAnnotation(UdfDefine.class);
              if (null == udfDefine) {
                return;
              }
              UdtfMeta udtfMeta =
                  new UdtfMeta(
                      udfDefine.name(),
                      udfDefine.compatibleName(),
                      udfDefine.description(),
                      udtfBaseClass);
              addUdtfMeta(udtfMeta);
              log.debug("addUdtfMeta,name=" + udfDefine.name());
            })
        .scan();
  }

  private void udfCheck() {
    // empty
  }

  private void addUdfMeta(IUdfMeta udfMeta) {
    Set<UdfName> nameSet = new HashSet<>();
    nameSet.add(UdfName.from(udfMeta.getName()));
    for (String compatibleName : udfMeta.getCompatibleNames()) {
      nameSet.add(UdfName.from(compatibleName));
    }
    for (UdfName udfName : nameSet) {
      Map<String, IUdfMeta> metaMap = udfMetaMap.computeIfAbsent(udfName, k -> new HashMap<>());
      String paramKeyString = UdfUtils.getTypeKeyString(udfMeta.getParamTypeList(), "(", ")");
      if (metaMap.containsKey(paramKeyString)) {
        throw new UdfExistsException("duplicated udf " + udfMeta, null);
      }
      metaMap.put(paramKeyString, udfMeta);
    }
  }

  private void addUdafMeta(UdafMeta udafMeta) {
    Set<UdfName> nameSet = new HashSet<>();
    nameSet.add(UdfName.from(udafMeta.getName()));
    for (String compatibleName : udafMeta.getCompatibleNames()) {
      nameSet.add(UdfName.from(compatibleName));
    }
    for (UdfName udfName : nameSet) {
      Map<String, UdafMeta> metaMap = udafMetaMap.computeIfAbsent(udfName, k -> new HashMap<>());
      String paramKeyString =
          UdfUtils.getTypeKeyString(Lists.newArrayList(udafMeta.getRowDataType()), "(", ")");
      if (metaMap.containsKey(paramKeyString)) {
        throw new UdfExistsException("duplicated udaf " + udafMeta, null);
      }
      metaMap.put(paramKeyString, udafMeta);
    }
  }

  private void addUdtfMeta(UdtfMeta udtfMeta) {
    Set<UdfName> nameSet = new HashSet<>();
    nameSet.add(UdfName.from(udtfMeta.getName()));
    for (String compatibleName : udtfMeta.getCompatibleNames()) {
      nameSet.add(UdfName.from(compatibleName));
    }
    for (UdfName udfName : nameSet) {
      Map<String, UdtfMeta> metaMap = udtfMetaMap.computeIfAbsent(udfName, k -> new HashMap<>());
      String paramKeyString = UdfUtils.getTypeKeyString(udtfMeta.getRowDataTypes(), "(", ")");
      if (metaMap.containsKey(paramKeyString)) {
        throw new UdfExistsException("duplicated udaf " + udtfMeta, null);
      }
      metaMap.put(paramKeyString, udtfMeta);
    }
  }

  @Override
  public IUdfMeta getUdfMeta(String name, List<KgType> paramTypeList) {
    return getMeta(name, paramTypeList, this.udfMetaMap);
  }

  @Override
  public RuntimeUdfMeta getRuntimeUdfMeta(String name) {
    Map<String, IUdfMeta> metaMap = udfMetaMap.get(UdfName.from(name));
    if (null == metaMap) {
      return null;
    }
    return new RuntimeUdfMeta(name, metaMap);
  }

  @Override
  public UdafMeta getUdafMeta(String name, KgType rowDataTypes) {
    return getMeta(name, Lists.newArrayList(rowDataTypes), this.udafMetaMap);
  }

  @Override
  public List<UdafMeta> getUdafMetas(String name) {
    Map<String, UdafMeta> subMetaMap = this.udafMetaMap.get(UdfName.from(name));
    if (null == subMetaMap) {
      return null;
    }
    return Lists.newArrayList(subMetaMap.values());
  }

  @Override
  public UdtfMeta getUdtfMeta(String name, List<KgType> rowDataTypes) {
    return getMeta(name, rowDataTypes, this.udtfMetaMap);
  }

  private <T> T getMeta(
      String name, List<KgType> parameterTypeList, Map<UdfName, Map<String, T>> metaMap) {
    Map<String, T> subMetaMap = metaMap.get(UdfName.from(name));
    if (null == subMetaMap) {
      return null;
    }
    Iterator<List<KgType>> compatibleParamTypeIt =
        UdfUtils.getAllCompatibleParamTypeList(parameterTypeList);
    while (compatibleParamTypeIt.hasNext()) {
      List<KgType> typeList = compatibleParamTypeIt.next();
      T rst = subMetaMap.get(UdfUtils.getTypeKeyString(typeList, "(", ")"));
      if (null != rst) {
        return rst;
      }
    }
    return null;
  }

  @Override
  public List<IUdfMeta> getAllUdfMeta() {
    return getAllMeta(this.udfMetaMap);
  }

  @Override
  public List<RuntimeUdfMeta> getAllRuntimeUdfMeta() {
    List<RuntimeUdfMeta> udfMetaList = new ArrayList<>();
    for (Map.Entry<UdfName, Map<String, IUdfMeta>> entry : this.udfMetaMap.entrySet()) {
      udfMetaList.add(new RuntimeUdfMeta(entry.getKey().toString(), entry.getValue()));
    }
    return udfMetaList;
  }

  @Override
  public List<UdafMeta> getAllUdafMeta() {
    return getAllMeta(this.udafMetaMap);
  }

  @Override
  public List<UdtfMeta> getAllUdtfMeta() {
    return getAllMeta(this.udtfMetaMap);
  }

  public <T> List<T> getAllMeta(Map<UdfName, Map<String, T>> metaMap) {
    List<T> metaList = new ArrayList<>();
    for (Map<String, T> subMetaMap : metaMap.values()) {
      metaList.addAll(subMetaMap.values());
    }
    return metaList;
  }

  @Override
  public Map<String, Map<String, UdfParameterTypeHint>> getUdfTypeHint() {
    Map<String, Map<String, UdfParameterTypeHint>> allTypeHints = new HashMap<>();
    Map<String, UdfParameterTypeHint> typeHints =
        allTypeHints.computeIfAbsent("udf", k -> new HashMap<>());
    for (Map.Entry<UdfName, Map<String, IUdfMeta>> entry : this.udfMetaMap.entrySet()) {
      String name = entry.getKey().toString();
      List<Tuple2<List<KgType>, List<KgType>>> typeInfoList = new ArrayList<>();
      for (IUdfMeta meta : entry.getValue().values()) {
        typeInfoList.add(
            new Tuple2<>(meta.getParamTypeList(), Lists.newArrayList(meta.getResultType())));
      }
      typeHints.put(name, new UdfParameterTypeHint(name, typeInfoList));
    }
    typeHints = allTypeHints.computeIfAbsent("udaf", k -> new HashMap<>());
    for (Map.Entry<UdfName, Map<String, UdafMeta>> entry : this.udafMetaMap.entrySet()) {
      String name = entry.getKey().toString();
      List<Tuple2<List<KgType>, List<KgType>>> typeInfoList = new ArrayList<>();
      for (UdafMeta meta : entry.getValue().values()) {
        typeInfoList.add(
            new Tuple2<>(
                Lists.newArrayList(meta.getRowDataType()),
                Lists.newArrayList(meta.getResultType())));
      }
      typeHints.putIfAbsent(name, new UdfParameterTypeHint(name, typeInfoList));
    }
    typeHints = allTypeHints.computeIfAbsent("udtf", k -> new HashMap<>());
    for (Map.Entry<UdfName, Map<String, UdtfMeta>> entry : this.udtfMetaMap.entrySet()) {
      String name = entry.getKey().toString();
      List<Tuple2<List<KgType>, List<KgType>>> typeInfoList = new ArrayList<>();
      for (UdtfMeta meta : entry.getValue().values()) {
        typeInfoList.add(new Tuple2<>(meta.getRowDataTypes(), meta.getResultTypes()));
      }
      typeHints.putIfAbsent(name, new UdfParameterTypeHint(name, typeInfoList));
    }
    return allTypeHints;
  }
}
