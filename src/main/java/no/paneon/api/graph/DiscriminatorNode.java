package no.paneon.api.graph;

import org.apache.logging.log4j.Logger;

import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.LogManager;

public class DiscriminatorNode extends Node {
	
    static final Logger LOG = LogManager.getLogger(DiscriminatorNode.class);

	String node;
	String type;
	
	Node placedByNode;
	Place placedInDirection;
	
	public DiscriminatorNode(String name) {
		super();
		this.resource=name;
		
		LOG.debug("DiscriminatorNode: {}", this);
				
	}
	
	@LogMethod(level=LogLevel.TRACE)
	void setPlacement(Node node, Place direction) {
		this.placedByNode = node;
		this.placedInDirection = direction;
	}
			

	@LogMethod(level=LogLevel.TRACE)
	public void setFloatingPlacement() {
		// TODO Auto-generated method stub
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public String getType() {
		return type;
	}
	
	@Override
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		return false;
	}
		
}
