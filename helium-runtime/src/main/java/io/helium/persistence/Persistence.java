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

package io.helium.persistence;

import io.helium.common.Path;
import io.helium.event.changelog.ChangeLog;
import io.helium.json.Node;
import io.helium.persistence.queries.QueryEvaluator;
import io.helium.server.Endpoint;
import io.helium.server.protocols.websocket.WebsocketEndpoint;

import java.util.Optional;

public interface Persistence {

    Object get(Path path);

    Node getNode(ChangeLog log, Path path);

    boolean exists(Path path);

    void remove(ChangeLog log, Optional<Node> auth, Path path);

    void applyNewValue(ChangeLog log, long sequence, Optional<Node> auth, Path path, int priority, Object payload);

    void updateValue(ChangeLog log,long sequence,  Optional<Node> auth, Path path, int priority, Object payload);

    void setPriority(ChangeLog log, Optional<Node> auth, Path path, int priority);

    void syncPath(ChangeLog log, Path path, Endpoint handler);

    void syncPropertyValue(ChangeLog log, Path path, Endpoint heliumEventHandler);

    void syncPathWithQuery(ChangeLog log, Path path, WebsocketEndpoint handler,
                           QueryEvaluator queryEvaluator, String query);

    public static String prevChildName(Node parent, int priority) {
        if (priority <= 0) {
            return null;
        }
        return parent.keys().get(priority - 1);
    }

    public static long childCount(Object node) {
        return (node instanceof Node) ? ((Node) node).getChildren().size() : 0;
    }

    public static int priority(Node parentNode, String name) {
        return parentNode.indexOf(name);
    }

    public static boolean hasChildren(Object node) {
        return (node instanceof Node) ? ((Node) node).hasChildren() : false;
    }

}
