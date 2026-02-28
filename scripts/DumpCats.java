import java.sql.*;

public class DumpCats {
  public static void dump(String db) {
    String url = "jdbc:postgresql://localhost:5432/" + db;
    try (Connection c = DriverManager.getConnection(url, "postgres", "root");
         Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("select id,name from public.categories order by id")) {
      System.out.println("db=" + db);
      while (rs.next()) {
        System.out.println(rs.getLong(1)+"|"+rs.getString(2));
      }
    } catch (Exception e) {
      System.out.println("db="+db+" error="+e.getMessage());
    }
  }
  public static void main(String[] args){ dump("smartpantry"); dump("funddb"); }
}
