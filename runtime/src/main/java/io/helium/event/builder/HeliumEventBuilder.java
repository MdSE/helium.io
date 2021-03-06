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

package io.helium.event.builder;

import io.helium.common.Path;
import io.helium.event.HeliumEvent;
import io.helium.event.HeliumEventType;
import org.vertx.java.core.json.JsonObject;

import java.util.Optional;

public class HeliumEventBuilder {

    private HeliumEvent underConstruction;

    private HeliumEventBuilder() {
        this.underConstruction = new HeliumEvent();
    }

    public static HeliumEventBuilder start() {
        return new HeliumEventBuilder();
    }

    public HeliumEvent build() {
        if (!this.underConstruction.containsField(HeliumEvent.AUTH)) {
            this.underConstruction.putObject(HeliumEvent.AUTH, new JsonObject());
        }
        return this.underConstruction;
    }

    public HeliumEventBuilder withPayload(Object payload) {
        underConstruction.putValue(HeliumEvent.PAYLOAD, payload);
        return this;
    }

    public HeliumEventBuilder type(HeliumEventType type) {
        underConstruction.putString(HeliumEvent.TYPE, type.toString());
        return this;
    }

    public HeliumEventBuilder path(String path) {
        underConstruction.putString(HeliumEvent.PATH, path);
        return this;
    }

    public HeliumEventBuilder auth(String auth) {
        underConstruction.putString(HeliumEvent.AUTH, auth);
        return this;
    }

    public HeliumEventBuilder withAuth(Optional<JsonObject> auth) {
        if (auth.isPresent()) {
            underConstruction.putObject(HeliumEvent.AUTH, auth.get());
        }
        return this;
    }

    public HeliumEventBuilder name(String name) {
        underConstruction.putString(HeliumEvent.NAME, name);
        return this;
    }

    public static HeliumEventBuilder set(Path path, Object value) {
        return start().type(HeliumEventType.SET).path(path.toString()).withPayload(value);
    }

    public static HeliumEventBuilder update(Path path, Object value) {
        return start().type(HeliumEventType.UPDATE).path(path.toString()).withPayload(value);
    }

    public static HeliumEventBuilder push(Path path, Object value) {
        return start().type(HeliumEventType.PUSH).path(path.toString()).withPayload(value);
    }

    public static HeliumEventBuilder delete(Path path) {
        return start().type(HeliumEventType.DELETE).path(path.toString());
    }

    public static HeliumEventBuilder get(Path path) {
        return start().type(HeliumEventType.GET).path(path.toString());
    }
}
