package owl.tutorial.dbconnection;

import java.io.File;
import java.util.Scanner;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.jdbc.PreparedStatement;
import org.neo4j.jdbc.ResultSet;
import org.neo4j.jdbc.Connection;
import org.neo4j.cypher.internal.ExecutionEngine;
import org.neo4j.cypher.internal.javacompat.ExecutionResult;
import org.neo4j.driver.*;
import org.neo4j.driver.v1.Statement;
import org.neo4j.driver.v1.Transaction;


public class DbConnection {
	public static Connection neoConnection()
	{
		// Connecting
		Connection con = null;
		try {
			con = DriverMxanager.getConnection("jdbc:neo4j:bolt://localhost", "neo4j", "root");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return con;
	}
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		System.out.println("Enter your query : ");
		String query = sc.next();
		executeQuery(query);

	}
	private static void executeQuery(String query) {
		// TODO Auto-generated method stubs
//		Class.forName("org.neo4j.jdbc.Driver");
//		// Connect
//		Connection con = (Connection) DriverManager.getConnection("jdbc:neo4j:bolt://localhost");
//
//		// Querying
//		try (Statement stmt = con.createStatement()) {
//		    ResultSet rs = stmt.executeQuery("MATCH (n:User) RETURN n.name");
//		    while (rs.next()) {
//		        System.out.println(rs.getString("n.name"));
//		    }
//		}
//		con.close();		
		Connection con = DbConnection.neoConnection();
		try (PreparedStatement stmt = (java.sql.PreparedStatement) con.prepareStatement(query)) {

//			stmt.setString(1,"John");
			
			try (ResultSet rs = ()) {
//				while (rs.next()) {
//					System.out.println("Friend: "+rs.getString("f.name")+" is "+rs.getInt("f.age"));
				}
			}
		}

	}
}
