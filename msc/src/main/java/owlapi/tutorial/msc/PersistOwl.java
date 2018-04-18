package owlapi.tutorial.msc;
import java.io.File;
import java.util.*;

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
		File file = new File("/home/unknown/Desktop/owlfile.owl");
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
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(new File("/home/unknown/Desktop/n4jdb/databases/graph.db"));
		org.neo4j.graphdb.Transaction tx = graphDb.beginTx();
		try
		{
			Node thingNode =  getOrCreateNodeWithUniqueFactory(graphDb,"owl:Thing");
			for (OWLClass c :ontology.getClassesInSignature(true)) {
				String classString = c.toString();
				if (classString.contains("#")) {
					classString = classString.substring(classString.indexOf("#")+1,classString.lastIndexOf(">"));
				}
				classString = classString.trim();//remove extra spaces
				Node classNode = getOrCreateNodeWithUniqueFactory(graphDb,classString);//create node

				getAndCreateParents( graphDb, thingNode, classNode, c, reasoner );//populate Parents
				getAndCreateEquivilentClasses( graphDb, thingNode, classNode, c, reasoner );// find and create equivilent classes
				//getAnotationsAndModifyCode( graphDb, thingNode, classNode, c, reasoner );	//annotate classses		
				populateIndividualsOfClass( graphDb, thingNode, classNode, c, reasoner );// find and create instances classes
				
				tx.success();
			}
		}
		finally {
			tx.close();	 
			System.out.println("finished");			
		}
	}
	
	//fetch parents of class and create their node and link with it
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
	
	//get equivilent calsses and attach them with EquivilentTo Relationship
	private static void getAndCreateEquivilentClasses( GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner ){
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
	
	//get individuals  and attach them with individualOf Relationship
	private static void populateIndividualsOfClass( GraphDatabaseService graphDb, Node thingNode, Node classNode, OWLClass c, OWLReasoner reasoner ){

		NodeSet<OWLNamedIndividual> instances = reasoner.getInstances(c);//getSuperClasses
		if ( instances.isEmpty() ) {
			//do nothing
			//System.out.println("no instance found for + " + c.toString() );
		} else {
			for ( org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> instance : instances  ) {
				
					String parentString = instance.toString();
					if ( parentString.contains("#") ) {
						parentString = parentString.substring( parentString.indexOf("#") + 1, parentString.lastIndexOf(">") );
					}
					Node parentNode = getOrCreateNodeWithUniqueFactory( graphDb, parentString );
					parentNode.createRelationshipTo( classNode,DynamicRelationshipType.withName("instanceOf") );// cl--EquivalentTo-->c						
							
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
