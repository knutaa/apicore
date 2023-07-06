package no.paneon.api.graph;

import java.util.List;
import java.util.function.Predicate;

import org.apache.logging.log4j.Logger;

import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;

import org.apache.logging.log4j.LogManager;

public class Edge {
	
    static final Logger LOG = LogManager.getLogger(Edge.class);

	public String relation;
	public Node related;
	public String cardinality;
	public Node node;
	public boolean required=false;
	
	public boolean isMarked = false;
		
	public Edge(Node node, String relation, Node related, String cardinality, boolean required) {
		this.node=node;
		this.relation=relation;
		this.related=related;
		this.cardinality=cardinality;
		this.required=required;
	}
		
	private boolean isPlaced(List<Object> processed) {
		boolean res = processed.contains(this);
		
		LOG.debug("isPlaced: edge=" + this + " res=" + res );

		return res;
	}
	
	private void placed(List<Object> processed) {
		processed.add(this);
	}
	
	public String toString() {
		return "edge: " + node + " --> " + cardinality + " " + related + (!relation.isBlank() ? " : " + relation : "");
	}
	
	public boolean isRequired() {
		return required;
	}
	
	public boolean isEnumEdge() {
		return (this instanceof EdgeEnum);
	}
	
	public String getRelationship() {
		return this.relation;
	}
	
	public Node getRelated() {
		return this.related;
	}
	
	public Node getNode() {
		return this.node;
	}
	
	public boolean isRegularEdgeCore() {
		boolean isInheritance = this instanceof AllOf || this instanceof OneOf || this instanceof Discriminator;
		return !isInheritance;
	}

	public boolean isInheritance() {
		boolean isInheritance = isOneOf() || isAllOf() || isDiscriminator();
		return isInheritance && !flattenedInheritance();
	}

	public boolean isOneOf() {
		boolean oneOf = this instanceof OneOf;
		return oneOf && !flattenedInheritance();
	}
	
	public boolean isDiscriminator() {
		boolean discriminator = this instanceof Discriminator;
		return discriminator && !flattenedInheritance();
	}
	
	public boolean isNotDiscriminator() {
		return !isDiscriminator();
	}
	
	public boolean isAllOf() {
		boolean allOf = this instanceof AllOf;
		return allOf;
	}

	private boolean flattenedInheritance() {
		List<String> flatten = Config.getFlattenInheritance();
		boolean res = flatten.contains(this.related.getName());
		
//		LOG.debug("flattenedInheritance: name={} flatten={}", this.related.getName(), flatten);
		
		if(res) return res;
		
		List<String> flattenPattern = Config.getFlattenInheritanceRegexp();
		
//		LOG.debug("flattenedInheritance: name={} pattern={}", this.related.getName(), flattenPattern);
		res = flattenPattern.stream().anyMatch(pattern -> this.related.getName().matches(pattern));

//		LOG.debug("flattenedInheritance: name={} pattern={} res={}", this.related.getName(), flattenPattern, res);

		return res;
	}
	
	public void setMarked(boolean value) {
		
		LOG.debug("Edge::setMarked: from={} to={} marked={}", this.node, this.related, value); 

		this.isMarked = value;
	}
	
	public boolean isEdgeMatch(Edge e) {
		return this.related.equals(e.related) && this.relation.contentEquals(e.relation);
	}
	
	private boolean isVendorExtension=false;
	public void setVendorExtension() {
		LOG.debug("Edge::setVendorExtension: edge={}", this.relation);
		this.isVendorExtension=true;
	}

	public boolean getVendorExtension() {
		return this.isVendorExtension;
	}

	private boolean isCardinalityExtension=false;
	public void setCardinalityExtension() {
		this.isCardinalityExtension=true;
	}

	public boolean getCardinalityExtension() {
		return this.isCardinalityExtension;
	}
	
	private boolean isRequiredExtension=false;
	public void setRequiredExtension(boolean value) {
		this.isRequiredExtension=value;
	}

	public boolean getRequiredExtension() {
		return this.isRequiredExtension;
	}

	public String getSourceName() {
		return getNode().getName();
	}
	
}
