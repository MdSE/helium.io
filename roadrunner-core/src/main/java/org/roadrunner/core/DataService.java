package org.roadrunner.core;

import org.json.JSONObject;
import org.roadrunner.core.dtos.InitMessage;
import org.roadrunner.core.dtos.PushedMessage;

public interface DataService {

	void setListener(DataListener dataListener);

	PushedMessage update(String nodeName, JSONObject payload);

	void shutdown();

	void sync();

	InitMessage init(String path);
}
