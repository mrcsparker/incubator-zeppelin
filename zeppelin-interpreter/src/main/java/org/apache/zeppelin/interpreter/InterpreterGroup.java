/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.resource.ResourcePool;

/**
 * InterpreterGroup is list of interpreters in the same group.
 * And unit of interpreter instantiate, restart, bind, unbind.
 */
public class InterpreterGroup extends LinkedList<Interpreter>{
  String id;

  Logger LOGGER = Logger.getLogger(InterpreterGroup.class);

  AngularObjectRegistry angularObjectRegistry;
  RemoteInterpreterProcess remoteInterpreterProcess;    // attached remote interpreter process
  ResourcePool resourcePool;

  private static final Map<String, InterpreterGroup> allInterpreterGroups =
      new ConcurrentHashMap<String, InterpreterGroup>();

  public static InterpreterGroup get(String id) {
    return allInterpreterGroups.get(id);
  }

  public static Collection<InterpreterGroup> getAll() {
    return new LinkedList(allInterpreterGroups.values());
  }

  public InterpreterGroup(String id) {
    this.id = id;
    allInterpreterGroups.put(id, this);
  }

  public InterpreterGroup() {
    getId();
    allInterpreterGroups.put(id, this);
  }

  private static String generateId() {
    return "InterpreterGroup_" + System.currentTimeMillis() + "_"
           + new Random().nextInt();
  }

  public String getId() {
    synchronized (this) {
      if (id == null) {
        id = generateId();
      }
      return id;
    }
  }

  public Properties getProperty() {
    Properties p = new Properties();
    for (Interpreter intp : this) {
      p.putAll(intp.getProperty());
    }
    return p;
  }

  public AngularObjectRegistry getAngularObjectRegistry() {
    return angularObjectRegistry;
  }

  public void setAngularObjectRegistry(AngularObjectRegistry angularObjectRegistry) {
    this.angularObjectRegistry = angularObjectRegistry;
  }

  public RemoteInterpreterProcess getRemoteInterpreterProcess() {
    return remoteInterpreterProcess;
  }

  public void setRemoteInterpreterProcess(RemoteInterpreterProcess remoteInterpreterProcess) {
    this.remoteInterpreterProcess = remoteInterpreterProcess;
  }

  public void close() {
    List<Thread> closeThreads = new LinkedList<Thread>();

    for (final Interpreter intp : this) {
      Thread t = new Thread() {
        public void run() {
          intp.close();
        }
      };

      t.start();
      closeThreads.add(t);
    }

    for (Thread t : closeThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        LOGGER.error("Can't close interpreter", e);
      }
    }
  }

  public void destroy() {
    List<Thread> destroyThreads = new LinkedList<Thread>();

    for (final Interpreter intp : this) {
      Thread t = new Thread() {
        public void run() {
          intp.destroy();
        }
      };

      t.start();
      destroyThreads.add(t);
    }

    for (Thread t : destroyThreads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        LOGGER.error("Can't close interpreter", e);
      }
    }

    // make sure remote interpreter process terminates
    if (remoteInterpreterProcess != null) {
      while (remoteInterpreterProcess.referenceCount() > 0) {
        remoteInterpreterProcess.dereference();
      }
    }

    allInterpreterGroups.remove(id);
  }

  public void setResourcePool(ResourcePool resourcePool) {
    this.resourcePool = resourcePool;
  }

  public ResourcePool getResourcePool() {
    return resourcePool;
  }
}
