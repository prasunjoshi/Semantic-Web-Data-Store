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

public class OwlAPIFirst {
	public static void main(String[] args) {
		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		File file = new File("/home/sharad/Desktop/pizza.owl");
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
			Node thingNode =  getOrCreateNodeWithUniqueFactory(graphDb,"owl:Thing");
			for (OWLClass c :ontology.getClassesInSignature(true)) {
				String classString = c.toString();
				if (classString.contains("#")) {
					classString = classString.substring(classString.indexOf("#")+1,classString.lastIndexOf(">"));
				}
				Node classNode = getOrCreateNodeWithUniqueFactory(graphDb,classString);
				NodeSet<OWLClass> superclasses = reasoner.getSuperClasses(c, true);
				if (superclasses.isEmpty()) {
					classNode.createRelationshipTo(thingNode,DynamicRelationshipType.withName("isA"));
				} else {
					for (org.semanticweb.owlapi.reasoner.Node<OWLClass> parentOWLNode: superclasses) {
						OWLClassExpression parent = parentOWLNode.getRepresentativeElement();
						String parentString = parent.toString();
						if (parentString.contains("#")) {
							parentString = parentString.substring(parentString.indexOf("#")+1,parentString.lastIndexOf(">"));
						}
						Node parentNode = getOrCreateNodeWithUniqueFactory(graphDb,parentString);
						classNode.createRelationshipTo(parentNode,DynamicRelationshipType.withName("isA"));
					}
				}
				for (org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> in
						: reasoner.getInstances(c, true)) {
					OWLNamedIndividual i = in.getRepresentativeElement();
					String indString = i.toString();
					if (indString.contains("#")) {
						indString = indString.substring(
								indString.indexOf("#")+1,indString.lastIndexOf(">"));
					}
					Node individualNode = getOrCreateNodeWithUniqueFactory(graphDb,indString);
					individualNode.createRelationshipTo(classNode,DynamicRelationshipType.withName("isA"));
					for (OWLObjectPropertyExpression objectProperty:
						ontology.getObjectPropertiesInSignature()) {
						for(org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> object: reasoner.getObjectPropertyValues(i,objectProperty)) {
							String reltype = objectProperty.toString();
							reltype = reltype.substring(reltype.indexOf("#")+1,
									reltype.lastIndexOf(">"));
							String s =
									object.getRepresentativeElement().toString();
							s = s.substring(s.indexOf("#")+1,s.lastIndexOf(">"));
							Node objectNode = getOrCreateNodeWithUniqueFactory(graphDb,s);
							individualNode.createRelationshipTo(objectNode,
									DynamicRelationshipType.withName(reltype));
						}
					}
					for (OWLDataPropertyExpression dataProperty:
						ontology.getDataPropertiesInSignature()) {
						for (OWLLiteral object: reasoner.getDataPropertyValues(
								i, dataProperty.asOWLDataProperty())) {
							String reltype =
									dataProperty.asOWLDataProperty().toString();
							reltype = reltype.substring(reltype.indexOf("#")+1, 
									reltype.lastIndexOf(">"));
							String s = object.toString();
							individualNode.setProperty(reltype, s);
						}
					}
				}
				tx.success();
			}
		}
		finally {
			tx.close();	 
			System.out.println("success");
		}
	}
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
