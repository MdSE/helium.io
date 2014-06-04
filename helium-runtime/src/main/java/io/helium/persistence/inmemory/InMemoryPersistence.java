/*
 * Copyright 2012 The Helium Project
 *
 * The Helium Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.helium.persistence.inmemory;

import io.helium.common.Path;
import io.helium.core.Core;
import io.helium.event.changelog.ChangeLog;
import io.helium.event.changelog.ChangeLogBuilder;
import io.helium.json.HashMapBackedNode;
import io.helium.json.Node;
import io.helium.persistence.ChildRemovedSubTreeVisitor;
import io.helium.persistence.Persistence;
import io.helium.persistence.authorization.Authorization;
import io.helium.persistence.authorization.Operation;
import io.helium.persistence.queries.QueryEvaluator;
import io.helium.server.Endpoint;
import io.helium.server.protocols.websocket.WebsocketEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class InMemoryPersistence implements Persistence {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryPersistence.class);
    private Node model = new HashMapBackedNode();
    private Core core;
    private Authorization authorization;

    @Override
    public Object get(Path path) {
        if (path == null || path.isEmtpy() || model.getObjectForPath(path) == null) {
            return model;
        } else {
            return model.getObjectForPath(path);
        }
    }

    @Override
    public Node getNode(ChangeLog log, Path path) {
        Node nodeForPath = model.getNodeForPath(log, path);
        return nodeForPath;
    }

    @Override
    public void remove(ChangeLog log, Optional<Node> auth, Path path) {
        String nodeName = path.lastElement();
        Path parentPath = path.parent();
        if(model.pathExists(path)) {
            Node node = model.getNodeForPath(log, parentPath).getNode(nodeName);

            if (authorization.isAuthorized(Operation.WRITE, auth,
                    path, node)) {
                Node parent = model.getNodeForPath(log, parentPath);
                node.accept(path, new ChildRemovedSubTreeVisitor(log));
                parent.remove(nodeName);
                log.addChildRemovedLogEntry(parentPath, nodeName, node);
                core.distributeChangeLog(log);
            }
        }
    }

    @Override
    public void syncPath(ChangeLog log, Path path, Endpoint handler) {
        Node node = model.getNodeForPath(log, path);
        for (String childNodeKey : node.keys()) {
            Object object = node.get(childNodeKey);
            boolean hasChildren = (object instanceof Node) ? ((Node) object).hasChildren() : false;
            int indexOf = node.indexOf(childNodeKey);
            int numChildren = (object instanceof Node) ? ((Node) object).length() : 0;
            if (object != null && object != HashMapBackedNode.NULL) {
                handler.fireChildAdded(childNodeKey, path, path.parent(), object, hasChildren,
                        numChildren, null, indexOf);
            }
        }
    }

    @Override
    public void syncPathWithQuery(ChangeLog log, Path path, WebsocketEndpoint handler,
                                  QueryEvaluator queryEvaluator, String query) {
        Node node = model.getNodeForPath(log, path);
        for (String childNodeKey : node.keys()) {
            Object object = node.get(childNodeKey);
            if (queryEvaluator.evaluateQueryOnValue(object, query)) {
                if (object != null && object != HashMapBackedNode.NULL) {
                    handler.fireQueryChildAdded(path, node, object);
                }
            }
        }
    }

    @Override
    public void syncPropertyValue(ChangeLog log, Path path, Endpoint handler) {
        Node node = model.getNodeForPath(log, path.parent());
        String childNodeKey = path.lastElement();
        if (node.has(path.lastElement())) {
            Object object = node.get(path.lastElement());
            handler.fireValue(childNodeKey, path, path.parent(), object, "",
                    node.indexOf(childNodeKey));
        } else {
            handler.fireValue(childNodeKey, path, path.parent(), "", "", node.indexOf(childNodeKey));
        }
    }

    @Override
    public void updateValue(ChangeLog log, long sequence, Optional<Node> auth, Path path, int priority, Object payload) {
        Node node;
        boolean created = false;
        if (!model.pathExists(path)) {
            created = true;
        }
        Node parent = model.getNodeForPath(log, path.parent());
        if (payload instanceof Node) {
            if (parent.has(path.lastElement())) {
                node = parent.getNode(path.lastElement());
                parent.setIndexOf(path.lastElement(), priority);
            } else {
                node = new HashMapBackedNode();
                parent.putWithIndex(path.lastElement(), node, priority);
            }
            node.populate(new ChangeLogBuilder(log, sequence, path, path.parent(), node), (Node) payload);
            if (created) {
                log.addChildAddedLogEntry(path.lastElement(), path.parent(), path.parent()
                                .parent(), payload, false, 0,
                        Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                        Node.priority(parent, path.lastElement())
                );
            } else {
                log.addChildChangedLogEntry(path.lastElement(), path.parent(), path.parent()
                                .parent(), payload, false, 0,
                        Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                        Node.priority(parent, path.lastElement())
                );
            }
        } else {
            parent.putWithIndex(path.lastElement(), payload, priority);

            if (created) {
                log.addChildAddedLogEntry(path.lastElement(), path.parent(), path.parent()
                                .parent(), payload, false, 0,
                        Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                        Node.priority(parent, path.lastElement())
                );
            } else {
                log.addChildChangedLogEntry(path.lastElement(), path.parent(), path.parent()
                                .parent(), payload, false, 0,
                        Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                        Node.priority(parent, path.lastElement())
                );
                log.addValueChangedLogEntry(path.lastElement(), path, path.parent(), payload,
                        Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                        Node.priority(parent, path.lastElement()));
            }
            log.addChildChangedLogEntry(path.parent().lastElement(), path.parent().parent(),
                    path.parent().parent().parent(), parent, false, 0,
                    Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                    Node.priority(parent, path.lastElement()));

        }
        logger.info("Model changed: " + model);
    }

    @Override
    public void applyNewValue(ChangeLog log, long sequence, Optional<Node> auth, Path path, int priority, Object payload) {
        boolean created = false;
        if (!model.pathExists(path)) {
            created = true;
        }
        if (authorization.isAuthorized(Operation.WRITE, auth,
                path, payload)) {
            Node parent = model.getNodeForPath(log, path.parent());
            if (payload instanceof Node) {
                Node node = new HashMapBackedNode();
                populate(new ChangeLogBuilder(log, sequence,path, path.parent(), node), path, auth, node,
                        (Node) payload);
                parent.putWithIndex(path.lastElement(), node, priority);
            } else {
                parent.putWithIndex(path.lastElement(), payload, priority);
            }

            if (created) {
                log.addChildAddedLogEntry(path.lastElement(), path.parent(), path.parent()
                                .parent(), payload, false, 0,
                        Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                        Node.priority(parent, path.lastElement())
                );
            } else {
                addChangeEvent(log, path);
            }
            {
                Path currentPath = path;
                while (!currentPath.isSimple()) {
                    log.addValueChangedLogEntry(currentPath.lastElement(), currentPath,
                            currentPath.parent(), model.getObjectForPath(currentPath), null, -1);
                    currentPath = currentPath.parent();
                }
            }
        }
        logger.info("Model changed: " + model);
    }

    public void populate(ChangeLogBuilder logBuilder, Path path, Optional<Node> auth, Node node, Node payload) {
        for (String key : payload.keys()) {
            Object value = payload.get(key);
            if (value instanceof Node) {
                if (authorization.isAuthorized(Operation.WRITE, auth, path.append(key), value)) {
                    Node childNode = new HashMapBackedNode();
                    if (node.has(key)) {
                        node.put(key, childNode);
                        logBuilder.addNew(key, childNode);
                    } else {
                        node.put(key, childNode);
                        logBuilder.addChangedNode(key, childNode);
                    }
                    populate(logBuilder.getChildLogBuilder(key), path.append(key), auth, childNode,
                            (Node) value);
                }
            } else {
                if (authorization.isAuthorized(Operation.WRITE, auth, path.append(key), value)) {
                    if (node.has(key)) {
                    }
                    logBuilder.addChange(key, value);
                } else {
                    logBuilder.addNew(key, value);
                }
                if (value == null) {
                    logBuilder.addRemoved(key, node.get(key));
                }
                node.put(key, value);
            }
        }
    }

    private void addChangeEvent(ChangeLog log, Path path) {
        Object payload = model.getObjectForPath(path);
        Node parent = model.getNodeForPath(log, path.parent());
        log.addChildChangedLogEntry(path.lastElement(), path.parent(), path.parent()
                        .parent(), payload, Node.hasChildren(payload), Node.childCount(payload),
                Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                Node.priority(parent, path.lastElement())
        );

        log.addValueChangedLogEntry(path.lastElement(), path.parent(), path.parent()
                        .parent(), payload, Node.prevChildName(parent, Node.priority(parent, path.lastElement())),
                Node.priority(parent, path.lastElement())
        );
        if (!path.isEmtpy()) {
            addChangeEvent(log, path.parent());
        }
    }

    @Override
    public void setPriority(ChangeLog log, Optional<Node> auth, Path path, int priority) {
        Node parent = model.getNodeForPath(log, path.parent());
        if (authorization.isAuthorized(Operation.WRITE, auth,
                path, parent)) {
            parent.setIndexOf(path.lastElement(), priority);
        }
    }

    public void setCore(Core core) {
        this.core = core;
    }

    public boolean exists(Path path) {
        return model.pathExists(path);
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }
}