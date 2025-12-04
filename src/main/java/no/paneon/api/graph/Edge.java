package no.paneon.api.graph;

import java.util.List;
import java.util.function.Predicate;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

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
	public boolean deprecated=false;
	
	public boolean isMarked = false;
	
	public String description;
		
	private boolean isRequiredExtension=false;

	private JSONObject examples = new JSONObject();
	static final String EXAMPLE = "example";
	static final String EXAMPLES = "examples";

	public Edge(Node node, String relation, Node related, String cardinality, boolean required, boolean isDeprecated, String optDescription) {
		this.node=node;
		this.relation=relation;
		this.related=related;
		this.cardinality=cardinality;
		this.required=required;
		this.deprecated=isDeprecated;
		this.description=optDescription;
	}
	
	public Edge(Node node, String relation, Node related, String cardinality, boolean required, boolean isDeprecated) {
		this.node=node;
		this.relation=relation;
		this.related=related;
		this.cardinality=cardinality;
		this.required=required;
		this.deprecated=isDeprecated;
		this.description="";
	}
	
	public Edge(Edge edge, Node newFrom) {
		this.node=edge.node;
		this.relation=edge.relation;
		this.cardinality=edge.cardinality;
		this.required=edge.required;
		this.deprecated=edge.deprecated;
		
		this.node=newFrom;
		this.related=edge.related;
		this.description=edge.description;

	}
		
//	public Edge(Node node, String relation, Node related, String cardinality, boolean isRequired) {
//		this.node=node;
//		this.relation=relation;
//		this.related=related;
//		this.cardinality=cardinality;
//		this.required=isRequired;
//	}

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
	
	public boolean isDeprecated() {
		return deprecated;
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
	
	public void setRequiredExtension(boolean value) {
		this.isRequiredExtension=value;
	}

	public boolean getRequiredExtension() {
		return this.isRequiredExtension;
	}

	public String getSourceName() {
		return getNode().getName();
	}

	public boolean getDeprecated() {
		return this.deprecated;
	}

	public String getDescription() {
		return this.description;
	}

	public JSONObject getExamples() {
		return this.examples;
	}
	
	public void setExamples(JSONObject examples) {
		JSONObject ex = new JSONObject();
		
		if(examples.has(EXAMPLES))
			ex.put(EXAMPLES, examples.get(EXAMPLES));
		
		if(examples.has(EXAMPLE))
			ex.put(EXAMPLE, examples.get(EXAMPLE));
				
		this.examples = ex;
		
	}
	
}
