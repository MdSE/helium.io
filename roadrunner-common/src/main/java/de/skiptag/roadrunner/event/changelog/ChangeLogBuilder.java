package de.skiptag.roadrunner.event.changelog;

import de.skiptag.roadrunner.common.Path;
import de.skiptag.roadrunner.json.Node;

public class ChangeLogBuilder {
	private Node node;
	private Path parentPath;
	private Path path;
	private ChangeLog log;

	public ChangeLogBuilder(ChangeLog log, Path path, Path parentPath, Node node) {
		this.log = log;
		this.path = path;
		this.parentPath = parentPath;
		this.node = node;

	}

	public ChangeLogBuilder getChildLogBuilder(String childName) {
		return new ChangeLogBuilder(log, path.append(childName), path,
				node.getNode(childName));
	}

	public void addChange(String name, Object value) {
		int priority = priority(node, name);
		log.addChildChangedLogEntry(name, path, parentPath, value,
				hasChildren(value), childCount(value),
				prevChildName(node, priority), priority);
		log.addValueChangedLogEntry(name, path.append(name), path, value,
				prevChildName(node, priority), priority);
	}

	public void addNew(String name, Object value) {
		int priority = priority(node, name);
		log.addChildAddedLogEntry(name, path, parentPath, value,
				hasChildren(value), childCount(value),
				prevChildName(node, priority), priority);
	}

	public void addChangedNode(String name, Node value) {
		int priority = priority(node, name);
		log.addChildChangedLogEntry(name, path, parentPath, value,
				hasChildren(value), childCount(value),
				prevChildName(node, priority), priority);
	}

	public void addRemoved(String name, Object value) {
		log.addChildRemovedLogEntry(path, name, value);
	}

	private String prevChildName(Node parent, int priority) {
		if (priority <= 0) {
			return null;
		}
		return parent.keys().get(priority - 1);
	}

	private long childCount(Object node) {
		return (node instanceof Node) ? ((Node) node).getChildren().size() : 0;
	}

	private int priority(Node parentNode, String name) {
		return parentNode.indexOf(name);
	}

	private boolean hasChildren(Object node) {
		return (node instanceof Node) ? ((Node) node).hasChildren() : false;
	}
}