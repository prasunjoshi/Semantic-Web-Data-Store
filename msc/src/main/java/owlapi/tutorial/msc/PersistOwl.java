package owlapi.tutorial.msc;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import org.apache.commons.io.filefilter.FileFileFilter;
import org.neo4j.graphdb.index.UniqueFactory;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
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

public class PersistOwl {
	public static void main(String[] args) {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		File file = new File("/home/sharad/Desktop/jtpFileUpload/uploads/"+LatestFile.getLatestFile());
		System.out.println(file.getName());
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
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File("/home/sharad/Desktop/n4jdbi/databases/graph.db"));
		org.neo4j.graphdb.Transaction tx = graphDb.beginTx();
		try
		{
			//create node for every class in ontology file using unique factory
			//start with the thing class
			Node thingNode =  getOrCreateNodeWithUniqueFactory(graphDb,"owl:Thing");
			
			for (OWLClass c :ontology.getClassesInSignature(true)) {
				String classString = c.toString();
				if (classString.contains("#")) {
					classString = classString.substring(classString.indexOf("#")+1,classString.lastIndexOf(">"));
				}
				classString = classString.trim();//remove extra spaces
				Node classNode = getOrCreateNodeWithUniqueFactory(graphDb,classString);//create node
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
				if (parentString.contains("#")) {
					parentString = parentString.substring(parentString.indexOf("#")+1,parentString.lastIndexOf(">"));
				}
				Node parentNode = getOrCreateNodeWithUniqueFactory(graphDb,parentString);
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
					//System.out.println( "hehe-->" +  cl.toString() );
					String parentString = cl.toString();
					if (parentString.contains("#")) {
						parentString = parentString.substring( parentString.indexOf("#") + 1, parentString.lastIndexOf(">") );
					}
					Node parentNode = getOrCreateNodeWithUniqueFactory( graphDb, parentString );
					classNode.createRelationshipTo(parentNode,DynamicRelationshipType.withName("EquivalentTo"));// cl--EquivalentTo-->c						
				}
			}
		}
		//System.out.println("creating equivalentTo relationship done ");
	}	
	
	@SuppressWarnings("deprecation")
	private static void getAndCreateDisjointClasses(GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner) {
		Set<OWLClass> disjointList = reasoner.getDisjointClasses(c).getFlattened();
		for(OWLClass disjcl: disjointList){
			String disjointClassString = disjcl.toString();
			if (disjointClassString.contains("#")) {
				disjointClassString = disjointClassString.substring( disjointClassString.indexOf("#") + 1, disjointClassString.lastIndexOf(">") );
			}
			Node disjointNode = getOrCreateNodeWithUniqueFactory(graphDb, disjointClassString);
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
			for (org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> in : reasoner.getInstances(c, true)) {
				OWLNamedIndividual i = in.getRepresentativeElement();
				String indString = i.toString();
				if (indString.contains("#")) {
					indString = indString.substring(indString.indexOf("#")+1,indString.lastIndexOf(">"));
				}
				Node individualNode = getOrCreateNodeWithUniqueFactory(graphDb,indString);
				individualNode.createRelationshipTo(classNode,DynamicRelationshipType.withName("instanceOf"));
				getAndCreateDataProperties(graphDb,ontology,thingNode, classNode, c, i, individualNode, reasoner);
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
				indString = indString.substring(indString.indexOf("#")+1,indString.lastIndexOf(">"));
				String reltype = objectProperty.toString();
				reltype = reltype.substring(reltype.indexOf("#")+1,reltype.lastIndexOf(">"));
				String s =object.getRepresentativeElement().toString();
				s = s.substring(s.indexOf("#")+1,s.lastIndexOf(">"));
				Node objectNode = getOrCreateNodeWithUniqueFactory(graphDb,s);
				individualNode.createRelationshipTo(objectNode,DynamicRelationshipType.withName(reltype));
			}
		}	
	}

	//get Data Properties of Individuals
	@SuppressWarnings("deprecation")
	private static void getAndCreateDataProperties(GraphDatabaseService graphDb, OWLOntology ontology, Node thingNode,	Node classNode, OWLClass c, OWLNamedIndividual i, Node individualNode, OWLReasoner reasoner) {
		for (OWLDataPropertyExpression dataProperty:ontology.getDataPropertiesInSignature()) {
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
