package owlapi.tutorial.msc;
import java.io.File;
import java.util.*;
import java.util.stream.Stream;
import org.neo4j.graphdb.index.UniqueFactory;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

public class PersistOwl1 {
	public static void main(String[] args) {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		File file = new File("/home/aditi/Protege_4.0.2/first.owl");
		OWLOntology o;

		try {
			o = man.loadOntologyFromOntologyDocument(file);
			//System.out.println(o);
			try {
				importOntology(o);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("deprecation")
	private static void importOntology(OWLOntology ontology) throws Exception {
		
		System.out.println(ontology);
		OWLReasonerFactory factory = new ReasonerFactory();
		OWLReasoner reasoner = factory.createReasoner(ontology);
		if (!reasoner.isConsistent()) {
			throw new Exception("Ontology is inconsistent");
		}
		GraphDatabaseService graphDb;
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File("/home/aditi/Desktop/Neo4jDB/data/databases/graph.db"));
		org.neo4j.graphdb.Transaction tx = graphDb.beginTx();
		try
		{
			//create node for every class in ontology file using unique factory
			//start with the thing class
			Node thingNode =  getOrCreateNodeWithUniqueFactory(graphDb,"owl:Thing");
			
			for ( OWLClass c : ontology.getClassesInSignature(true) ) {//loop through all classes
				String classString = c.toString();
				Node classNode = getGraphNode( graphDb, classString, "Class" );//create node
				getAndCreateParents( graphDb, thingNode, classNode, c, reasoner );//populate Parents
				getAndCreateEquivalentClasses( graphDb, thingNode, classNode, c, reasoner );// find and create equivilent classes
				getAndCreateDisjointClasses(graphDb,thingNode,classNode,c,reasoner); //find and create disjoint classes
				//getAnotationsAndModifyCode( graphDb, thingNode, classNode, c, reasoner );	//annotate classses		
				populateIndividualsOfClass( graphDb, ontology,thingNode, classNode, c, reasoner );// find and create instances classes
				tx.success();
			}
		}
		finally {
			tx.close();	 
			System.out.println("finished");			
		}
	}
	
	//fetch parents of class and create their node and link with it
	@SuppressWarnings("deprecation")
	private static void getAndCreateParents( GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner ){
		
		NodeSet<OWLClass> superclasses = reasoner.getSuperClasses(c, true);//getSuperClasses
		if (superclasses.isEmpty()) {
			classNode.createRelationshipTo(thingNode,DynamicRelationshipType.withName("SubClassOf"));
		} else {
			for (org.semanticweb.owlapi.reasoner.Node<OWLClass> parentOWLNode: superclasses) {
				
				OWLClassExpression parent = parentOWLNode.getRepresentativeElement();
				String parentString = parent.toString();
				Node parentNode = getGraphNode( graphDb, parentString, "Class" );
				classNode.createRelationshipTo(parentNode,DynamicRelationshipType.withName("SubClassOf"));
				
			}
		}
		
	}
	
	//get equivalent classes and attach them with EquivalentTo Relationship
	@SuppressWarnings("deprecation")
	private static void getAndCreateEquivalentClasses( GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner ){
		//System.out.println("creating equivalentTo relationship start");
		Set<OWLClass> equivalentclasses = reasoner.getEquivalentClasses(c).getEntities();//getSuperClasses
		if (equivalentclasses.isEmpty()) {
			//do nothing
		} else {
			for ( OWLClass cl : equivalentclasses) {
				if( !c.toString().equals(cl.toString()) ) {//if class is not itself
					
					String parentString = cl.toString();
					Node parentNode = getGraphNode( graphDb, parentString, "Class" );
					classNode.createRelationshipTo(parentNode,DynamicRelationshipType.withName("EquivalentTo"));// cl--EquivalentTo-->c						
				}
			}
		}
		//System.out.println("creating equivalentTo relationship done ");
	}	
	
	//get distinct classes and join them using disjointWith Relationship
	@SuppressWarnings("deprecation")
	private static void getAndCreateDisjointClasses(GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner) {
		Set<OWLClass> disjointList = reasoner.getDisjointClasses(c).getFlattened();
		for(OWLClass disjcl: disjointList){
			String disjointClassString = disjcl.toString();
			Node disjointNode = getGraphNode( graphDb, disjointClassString, "Class" );
			classNode.createRelationshipTo(disjointNode,DynamicRelationshipType.withName("DisjointWith")); //disjcl--DisjointWith-->c
		}

	}
	
	//get annotaions and set them
	@SuppressWarnings("deprecation")
	private static void getAnotationsAndModifyCode( GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner ){
		System.out.println("Annotating Started");
		Set<OWLAnnotationProperty> annotations = c.getAnnotationPropertiesInSignature();//getSuperClasses
		if ( annotations.isEmpty() ) {
			//do nothing
			System.out.println("no annotation for class " + c.toString() );
		} else {
			System.out.println("following annotation for class " + c.toString() );			
			for ( OWLAnnotationProperty oap : annotations) {
					System.out.println( "oap-->"+oap.toString() );
			}
		}
		System.out.println("Annotating Stopped");
	}	
	
	//get individuals and attach them with individualOf Relationship
	@SuppressWarnings("deprecation")
	private static void populateIndividualsOfClass( GraphDatabaseService graphDb, OWLOntology ontology,Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner ){
		NodeSet<OWLNamedIndividual> instances = reasoner.getInstances(c);//getSuperClasses
		if ( instances.isEmpty() ) {
		} else {
			
			for ( org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> in : reasoner.getInstances(c, true) ) {
				//get Individual of a class
				OWLNamedIndividual i = in.getRepresentativeElement();
				String indString = i.toString();
				Node individualNode = getGraphNode( graphDb,indString, "Individual" );
				individualNode.createRelationshipTo(classNode,DynamicRelationshipType.withName("instanceOf"));
				
				//get Distinct Individuals for an individual
				for( org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> distinctIndividuals : reasoner.getDifferentIndividuals(i)) {
					OWLNamedIndividual distinctIndividual = distinctIndividuals.getRepresentativeElement();
					String disIndString = distinctIndividual.toString();
					Node distinctIndividualNode = getGraphNode(graphDb, disIndString,"Individual");
					distinctIndividualNode.createRelationshipTo(individualNode,DynamicRelationshipType.withName("distinctIndividual"));
					//System.out.println(indString+" different individual  "+disIndString);
				}
				
				//get Same Individuals for an individual
				for(OWLNamedIndividual sameIndividual : reasoner.getSameIndividuals(i)) {
					String sameIndividualString = sameIndividual.toString();
					if(!sameIndividualString.equals(indString)) {
						Node sameIndividualNode = getGraphNode(graphDb, sameIndividualString,"Individual");
						sameIndividualNode.createRelationshipTo(individualNode,DynamicRelationshipType.withName("sameIndividual"));
						//System.out.println(indString+" same individual as "+sameIndividualString);
					}
				}
				
				//call to get and create data properties for an individual
				getAndCreateDataProperties(graphDb,ontology,thingNode, classNode, c, i, individualNode, reasoner);
				
				//call to get and create object properties for an individual
				getAndCreateObjectProperties(graphDb,ontology,thingNode,classNode,c,i,individualNode,reasoner);	
			}
		}
	}	
	
	//get Object Properties of Individuals
	@SuppressWarnings("deprecation")
	private static void getAndCreateObjectProperties(GraphDatabaseService graphDb, OWLOntology ontology, Node thingNode,Node classNode, OWLClass c, OWLNamedIndividual i, Node individualNode, OWLReasoner reasoner) {
		for (OWLObjectPropertyExpression objectProperty:ontology.getObjectPropertiesInSignature()) {
			for(org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> object: reasoner.getObjectPropertyValues(i,objectProperty)) {
			
				String indString = i.toString();
				if( indString.contains("#") )
					indString = indString.substring(indString.indexOf("#") + 1, indString.lastIndexOf(">") );
				
				String reltype = objectProperty.toString();
				if( reltype.contains("#") )
					reltype = reltype.substring(reltype.indexOf("#")+1,reltype.lastIndexOf(">"));//?
				
				String s = object.getRepresentativeElement().toString();
				Node objectNode = getGraphNode( graphDb , s, "Individual" );
				individualNode.createRelationshipTo( objectNode, DynamicRelationshipType.withName(reltype) );
				
			}
			getAndLinkObjectPropertyDomains( graphDb, ontology, thingNode, classNode, c, objectProperty, reasoner );			
			
		}	
	}

	//get Data Properties of Individuals
	@SuppressWarnings("deprecation")
	private static void getAndCreateDataProperties(GraphDatabaseService graphDb, OWLOntology ontology, Node thingNode,	Node classNode, OWLClass c, OWLNamedIndividual i, Node individualNode, OWLReasoner reasoner) {
		for ( OWLDataPropertyExpression dataProperty:ontology.getDataPropertiesInSignature() ) {
			for (OWLLiteral object: reasoner.getDataPropertyValues(i, dataProperty.asOWLDataProperty())) {
				String reltype = dataProperty.asOWLDataProperty().toString();
				reltype = reltype.substring(reltype.indexOf("#")+1, reltype.lastIndexOf(">"));
				String s = object.toString();
				if (s.contains("#")) {
					s = s.substring(s.indexOf("#")+1,s.lastIndexOf(">"));
				}
				individualNode.setProperty(reltype, s);
			}
		}
	
	}
	
	//this method is called from getAndCreateObjectProperties, links object to its domains
	@SuppressWarnings("deprecation")
	private static void getAndLinkObjectPropertyDomains( GraphDatabaseService graphDb, OWLOntology ontology, Node thingNode, Node classNode, OWLClass c, OWLObjectPropertyExpression op, OWLReasoner reasoner) {
		
		NodeSet<OWLClass> domains = reasoner.getObjectPropertyDomains( op );
		
		String objectString = op.toString();
		Node objectNode = getGraphNode( graphDb, objectString, "ObjectProperty" );//get Node

		for (  OWLClass domain : domains.getFlattened() ) {

		    String domString = domain.toString();
			Node domNode = getGraphNode( graphDb, domString, "Domain" );
			objectNode.createRelationshipTo( domNode, DynamicRelationshipType.withName( "hasDomain" ) );
			objectNode.createRelationshipTo( domNode, DynamicRelationshipType.withName( "isDomainOf" ) );				
			
		}	
		
	}	
	
	//creates and return graph node
	public static Node getGraphNode( GraphDatabaseService graphDb, String str, String label ){
		str = str.trim();
		int startIndex = str.indexOf("#");
		int endIndex = str.indexOf(">");
		if( ( startIndex != -1 ) && ( endIndex != -1 ) )
			str = str.substring( startIndex + 1, endIndex );
		
		Node objectNode = getOrCreateNodeWithUniqueFactory( graphDb, str );//get Node
		org.neo4j.graphdb.Label objLabel = DynamicLabel.label( label );	//create Label	
		objectNode.addLabel( objLabel );//add label to node
		
		return objectNode;
	}
	
	//lib fcn dont update it
	private static Node getOrCreateNodeWithUniqueFactory(GraphDatabaseService graphDb, String nodeName) {
		UniqueFactory<Node> factory = new UniqueFactory.UniqueNodeFactory(graphDb, "index") {
			@Override
			protected void initialize(Node created, Map<String, Object> properties) {
				created.setProperty("className", properties.get("name"));
			}
		};
		return factory.getOrCreate("name", nodeName);
	}	
}

