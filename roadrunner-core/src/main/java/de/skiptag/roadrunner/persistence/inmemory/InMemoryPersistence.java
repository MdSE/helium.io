package de.skiptag.roadrunner.persistence.inmemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.skiptag.roadrunner.Roadrunner;
import de.skiptag.roadrunner.authorization.Authorization;
import de.skiptag.roadrunner.authorization.RoadrunnerOperation;
import de.skiptag.roadrunner.authorization.rulebased.RulesDataSnapshot;
import de.skiptag.roadrunner.common.Path;
import de.skiptag.roadrunner.event.changelog.ChangeLog;
import de.skiptag.roadrunner.event.changelog.ChangeLogBuilder;
import de.skiptag.roadrunner.json.Node;
import de.skiptag.roadrunner.json.NodeVisitor;
import de.skiptag.roadrunner.messaging.RoadrunnerEndpoint;
import de.skiptag.roadrunner.persistence.Persistence;
import de.skiptag.roadrunner.queries.QueryEvaluator;

public class InMemoryPersistence implements Persistence {

	private final class ChildRemovedSubTreeVisitor implements NodeVisitor {
		private ChangeLog	log;

		public ChildRemovedSubTreeVisitor(ChangeLog log) {
			this.log = log;
		}

		@Override
		public void visitProperty(Path path, Node node, String key, Object value) {
			log.addChildRemovedLogEntry(path, key, value);
		}

		@Override
		public void visitNode(Path path, Node node) {
			log.addChildRemovedLogEntry(path.getParent(), path.getLastElement(), null);
		}
	}

	private static final Logger	logger	= LoggerFactory.getLogger(InMemoryPersistence.class);

	private Node								model		= new Node();

	private Roadrunner					roadrunner;

	private Authorization				authorization;

	public InMemoryPersistence(Authorization authorization, Roadrunner roadrunner) {
		this.roadrunner = roadrunner;
		this.authorization = authorization;
	}

	@Override
	public Object get(Path path) {
		if (path == null || model.getObjectForPath(path) == null) {
			return model;
		} else {
			return model.getObjectForPath(path);
		}
	}

	@Override
	public Node getNode(Path path) {
		ChangeLog log = new ChangeLog();
		Node nodeForPath = model.getNodeForPath(log, path);
		roadrunner.distributeChangeLog(log);
		return nodeForPath;
	}

	@Override
	public void remove(ChangeLog log, Node auth, Path path) {
		String nodeName = path.getLastElement();
		Path parentPath = path.getParent();
		Node node = model.getNodeForPath(log, parentPath).getNode(nodeName);

		if (authorization.isAuthorized(RoadrunnerOperation.WRITE, auth,
				new InMemoryDataSnapshot(model), path, node)) {
			Node parent = model.getNodeForPath(log, parentPath);
			node.accept(path, new ChildRemovedSubTreeVisitor(log));
			parent.remove(nodeName);
			log.addChildRemovedLogEntry(parentPath, nodeName, node);
			roadrunner.distributeChangeLog(log);
		}
	}

	@Override
	public void syncPath(Path path, RoadrunnerEndpoint handler) {

		ChangeLog log = new ChangeLog();
		Node node = model.getNodeForPath(log, path);

		for (String childNodeKey : node.keys()) {
			Object object = node.get(childNodeKey);
			boolean hasChildren = (object instanceof Node) ? ((Node) object).hasChildren() : false;
			int indexOf = node.indexOf(childNodeKey);
			int numChildren = (object instanceof Node) ? ((Node) object).length() : 0;
			if (object != null && object != Node.NULL) {
				handler.fireChildAdded(childNodeKey, path, path.getParent(), object, hasChildren,
						numChildren, null, indexOf);
			}
		}
		roadrunner.distributeChangeLog(log);

	}

	@Override
	public void syncPathWithQuery(Path path, RoadrunnerEndpoint handler,
			QueryEvaluator queryEvaluator, String query) {
		ChangeLog log = new ChangeLog();
		Node node = model.getNodeForPath(log, path);
		for (String childNodeKey : node.keys()) {
			Object object = node.get(childNodeKey);
			if (queryEvaluator.evaluateQueryOnValue(object, query)) {
				if (object != null && object != Node.NULL) {
					handler.fireQueryChildAdded(path, node, object);
				}
			}
		}
		roadrunner.distributeChangeLog(log);
	}

	@Override
	public void syncPropertyValue(Path path, RoadrunnerEndpoint handler) {
		ChangeLog log = new ChangeLog();
		Node node = model.getNodeForPath(log, path.getParent());
		String childNodeKey = path.getLastElement();
		if (node.has(path.getLastElement())) {
			Object object = node.get(path.getLastElement());
			handler.fireValue(childNodeKey, path, path.getParent(), object, "",
					node.indexOf(childNodeKey));
		} else {
			handler.fireValue(childNodeKey, path, path.getParent(), "", "", node.indexOf(childNodeKey));
		}
		roadrunner.distributeChangeLog(log);

	}

	@Override
	public void updateValue(ChangeLog log, Node auth, Path path, int priority, Object payload) {
		Node node;
		boolean created = false;
		if (!model.pathExists(path)) {
			created = true;
		}
		Node parent = model.getNodeForPath(log, path.getParent());
		if (payload instanceof Node) {
			if (parent.has(path.getLastElement())) {
				node = parent.getNode(path.getLastElement());
				parent.setIndexOf(path.getLastElement(), priority);
			} else {
				node = new Node();
				parent.putWithIndex(path.getLastElement(), node, priority);
			}
			node.populate(new ChangeLogBuilder(log, path, path.getParent(), node), (Node) payload);
			if (created) {
				log.addChildAddedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
						.getParent(), payload, false, 0,
						prevChildName(parent, priority(parent, path.getLastElement())),
						priority(parent, path.getLastElement()));
			} else {
				log.addChildChangedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
						.getParent(), payload, false, 0,
						prevChildName(parent, priority(parent, path.getLastElement())),
						priority(parent, path.getLastElement()));
			}
		} else {
			parent.putWithIndex(path.getLastElement(), payload, priority);

			if (created) {
				log.addChildAddedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
						.getParent(), payload, false, 0,
						prevChildName(parent, priority(parent, path.getLastElement())),
						priority(parent, path.getLastElement()));
			} else {
				log.addChildChangedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
						.getParent(), payload, false, 0,
						prevChildName(parent, priority(parent, path.getLastElement())),
						priority(parent, path.getLastElement()));
				log.addValueChangedLogEntry(path.getLastElement(), path, path.getParent(), payload,
						prevChildName(parent, priority(parent, path.getLastElement())),
						priority(parent, path.getLastElement()));
			}
			log.addChildChangedLogEntry(path.getParent().getLastElement(), path.getParent().getParent(),
					path.getParent().getParent().getParent(), parent, false, 0,
					prevChildName(parent, priority(parent, path.getLastElement())),
					priority(parent, path.getLastElement()));

		}
		logger.trace("Model changed: " + model);
	}

	@Override
	public void applyNewValue(ChangeLog log, Node auth, Path path, int priority, Object payload) {
		boolean created = false;
		if (!model.pathExists(path)) {
			created = true;
		}
		if (authorization.isAuthorized(RoadrunnerOperation.WRITE, auth,
				new InMemoryDataSnapshot(model), path, payload)) {
			Node parent = model.getNodeForPath(log, path.getParent());
			if (payload instanceof Node) {
				Node node = new Node();
				populate(new ChangeLogBuilder(log, path, path.getParent(), node), path, auth, node,
						(Node) payload);
				parent.putWithIndex(path.getLastElement(), node, priority);
			} else {
				parent.putWithIndex(path.getLastElement(), payload, priority);
			}

			if (created) {
				log.addChildAddedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
						.getParent(), payload, false, 0,
						prevChildName(parent, priority(parent, path.getLastElement())),
						priority(parent, path.getLastElement()));
			} else {
				addChangeEvent(log, path);
			}
			{
				Path currentPath = path;
				while (!currentPath.isSimple()) {
					log.addValueChangedLogEntry(currentPath.getLastElement(), currentPath,
							currentPath.getParent(), model.getObjectForPath(currentPath), null, -1);
					currentPath = currentPath.getParent();
				}
			}
		}
		logger.trace("Model changed: " + model);
	}

	public void populate(ChangeLogBuilder logBuilder, Path path, Node auth, Node node, Node payload) {
		for (String key : payload.keys()) {
			Object value = payload.get(key);
			if (value instanceof Node) {
				if (authorization.isAuthorized(RoadrunnerOperation.WRITE, auth, new InMemoryDataSnapshot(
						model), path.append(key), value)) {
					Node childNode = new Node();
					populate(logBuilder.getChildLogBuilder(key), path.append(key), auth, childNode,
							(Node) value);
					if (node.has(key)) {
						node.put(key, childNode);
						logBuilder.addNew(key, childNode);
					} else {
						node.put(key, childNode);
						logBuilder.addChangedNode(key, childNode);
					}
				}
			} else {
				if (authorization.isAuthorized(RoadrunnerOperation.WRITE, auth, new InMemoryDataSnapshot(
						model), path.append(key), value)) {
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
		Node parent = model.getNodeForPath(log, path.getParent());
		log.addChildChangedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
				.getParent(), payload, hasChildren(payload), childCount(payload),
				prevChildName(parent, priority(parent, path.getLastElement())),
				priority(parent, path.getLastElement()));

		log.addValueChangedLogEntry(path.getLastElement(), path.getParent(), path.getParent()
				.getParent(), payload, prevChildName(parent, priority(parent, path.getLastElement())),
				priority(parent, path.getLastElement()));
		if (!path.isEmtpy()) {
			addChangeEvent(log, path.getParent());
		}
	}

	@Override
	public void setPriority(ChangeLog log, Node auth, Path path, int priority) {
		Node parent = model.getNodeForPath(log, path.getParent());
		if (authorization.isAuthorized(RoadrunnerOperation.WRITE, auth,
				new InMemoryDataSnapshot(model), path, parent)) {
			parent.setIndexOf(path.getLastElement(), priority);
		}
	}

	@Override
	public Node dumpSnapshot() {
		return model;
	}

	@Override
	public void restoreSnapshot(Node node) {
		model.populate(null, node);
	}

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

	@Override
	public RulesDataSnapshot getRoot() {
		return new InMemoryDataSnapshot(model);
	}
}