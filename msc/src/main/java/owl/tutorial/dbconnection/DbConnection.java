package owl.tutorial.dbconnection;

import java.sql.DriverManager;
import java.util.Scanner;

import org.neo4j.driver.internal.spi.Connection;
import org.neo4j.jdbc.PreparedStatement;
import org.neo4j.jdbc.ResultSet;
import org.semanticweb.owlapi.io.SystemOutDocumentTarget;

public class DbConnection {
	public static Connection neoConnection()
	{
		// Connecting
		try (Connection con = (Connection)DriverManager.getConnection("jdbc:neo4j:bolt://localhost:7687", "neo4j", "root")) {
			return con;
		    // Querying
//		    String query = "MATCH (u:User)-[:FRIEND]-(f:User) WHERE u.name = {1} RETURN f.name, f.age";
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		return null;
	}
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		System.out.println("Enter your query : ");
		String query = sc.next();
		executeQuery(query);

	}
	private static void executeQuery(String query) {
		// TODO Auto-generated method stubs
		Connection con = DbConnection.neoConnection();
	    try (PreparedStatement stmt = con.prepareStatement(query)) {
	    	
//        stmt.setString(1,"John");

//        try (ResultSet rs = stmt.execute()) {
//            while (rs.next()) {
//                System.out.println("Friend: "+rs.getString("f.name")+" is "+rs.getInt("f.age"));
//            }
//        }
    }
		
	}
}
