package owl.tutorial.dbconnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;

public class DbConnection
{
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		System.out.println("Enter your query : ");
		String query = sc.next();
		executeQuery(query);

	}

	public static void executeQuery(String query)
	{
		Connection connection = null;
		String neo_usernameString = "neo4j";
		String neo_passwordString = "sam12345";
		try
		{
			Class.forName("org.neo4j.jdbc.Driver");
			Properties properties = new Properties();
			properties.put("user", neo_usernameString);
			properties.put("password", neo_passwordString);
			connection = DriverManager.getConnection("jdbc:neo4j:http://localhost:7474/", properties);

			java.sql.PreparedStatement ps = connection.prepareStatement(query);
			java.sql.ResultSet rs = ps.executeQuery();
			ResultSetMetaData rsm = rs.getMetaData();
			int clm = rsm.getColumnCount();
			while(rs.next())
			{
				for(int i=1;i<=clm;i++){
					System.out.println(rs.getString(i)+" ");
				}
			}
		} catch (ClassNotFoundException e)
		{
			System.out.println("org.neo4j.jdbc.Driver not found");
			System.exit(1);
		} catch (SQLException e)
		{
			System.out.println("Cannot connect to Neo4j" + e.getMessage());
			System.exit(1);
		}
		
		System.out.println("Connection successfully established.");

	}

}