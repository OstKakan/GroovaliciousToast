/* This is the driving engine of the program. It parses the command-line
 * arguments and calls the appropriate methods in the other classes.
 *
 * You should edit this file in two ways:
 * 1) Insert your database username and password in the proper places.
 * 2) Implement the three functions getInformation, registerStudent
 *    and unregisterStudent.
 */
import org.postgresql.util.PSQLException;

import java.sql.*; // JDBC stuff.
import java.util.Properties;
import java.util.Scanner;
import java.io.*;  // Reading user input.

public class StudentPortal
{
    /* TODO Here you should put your database name, username and password */
    static final String USERNAME = "";
    static final String PASSWORD = "";

    /* Print command usage.
     * /!\ you don't need to change this function! */
    public static void usage () {
        System.out.println("Usage:");
        System.out.println("    i[nformation]");
        System.out.println("    r[egister] <course>");
        System.out.println("    u[nregister] <course>");
        System.out.println("    q[uit]");
    }

    /* main: parses the input commands.
     * /!\ You don't need to change this function! */
    public static void main(String[] args) throws Exception
    {
        try {
            Class.forName("org.postgresql.Driver");
            String url = "jdbc:postgresql://ate.ita.chalmers.se/";
            Properties props = new Properties();
            props.setProperty("user",USERNAME);
            props.setProperty("password",PASSWORD);
            Connection conn = DriverManager.getConnection(url, props);

            String student = args[0]; // This is the identifier for the student.

            Scanner scanner = new Scanner(System.in);
            //Console console = System.console();
            usage();
            System.out.println("Welcome!");
            while(true) {
                String mode = scanner.nextLine();
                //String mode = console.readLine("? > ");
                String[] cmd = mode.split(" +");
                cmd[0] = cmd[0].toLowerCase();
                if ("information".startsWith(cmd[0]) && cmd.length == 1) {
                    /* Information mode */
                    getInformation(conn, student);
                } else if ("register".startsWith(cmd[0]) && cmd.length == 2) {
                    /* Register student mode */
                    registerStudent(conn, student, cmd[1]);
                } else if ("unregister".startsWith(cmd[0]) && cmd.length == 2) {
                    /* Unregister student mode */
                    unregisterStudent(conn, student, cmd[1]);
                } else if ("quit".startsWith(cmd[0])) {
                    break;
                } else usage();
            }
            System.out.println("Goodbye!");
            conn.close();
        } catch (SQLException e) {
            System.err.println(e);
            System.exit(2);
        }
    }

    /* Given a student identification number, ths function should print
     * - the name of the student, the students national identification number
     *   and their university issued login name (something similar to a CID)
     * - the programme and branch (if any) that the student is following.
     * - the courses that the student has read, along with the grade.
     * - the courses that the student is registered to.
     * - the mandatory courses that the student has yet to read.
     * - whether or not the student fulfills the requirements for graduation
     */
    static void getInformation(Connection conn, String student) throws SQLException
    {
        System.out.println("Information for student " + student);
        System.out.println("-------------------------------------");

        PreparedStatement statement = conn.prepareStatement("SELECT * FROM StudentsFollowing WHERE idnbr = ?");
        statement.setString(1, student);
        ResultSet rs = statement.executeQuery();

        if (rs.next()) {
            System.out.println("Name: " + rs.getString("name"));
            System.out.println("Student ID: " + rs.getString("loginid"));
            System.out.println("Programme: " + rs.getString("programme"));
            if (rs.getString("branch") != null)
                System.out.println("Branch: " + rs.getString("branch"));
        }
        rs.close();
        statement.close();

        statement = conn.prepareStatement("SELECT * FROM FinishedCourses WHERE studentid = ?");
        statement.setString(1, student);
        rs = statement.executeQuery();

        System.out.println();
        System.out.println("Read courses (code, credits: grade):");
        while (rs.next()) {
            System.out.printf("%s, %.1fp: %s\n", rs.getString("course"), rs.getDouble("credits"), rs.getString("grade"));
        }
        rs.close();
        statement.close();

        statement = conn.prepareStatement("SELECT * FROM Registrations WHERE student = ?");
        statement.setString(1, student);
        rs = statement.executeQuery();

        System.out.println();
        System.out.println("Registered courses (code: status):");
        while (rs.next()) {
            String status = rs.getString("status");
            String course = rs.getString("course");
            if (status.equals("waiting")) {
                PreparedStatement positionStatement = conn.prepareStatement("SELECT queuePosition FROM CourseQueuePositions WHERE student = ? AND course = ?");
                positionStatement.setString(1, student);
                positionStatement.setString(2, course);
                ResultSet posRS = positionStatement.executeQuery();
                if (posRS.next())
                    status += " as nr " + posRS.getInt(1);
                posRS.close();
                positionStatement.close();
            }

            System.out.printf("%s: %s\n", course, status);
        }
        rs.close();
        statement.close();

        System.out.println();
        statement = conn.prepareStatement("SELECT * FROM PathToGraduation WHERE studentid = ?");
        statement.setString(1, student);
        rs = statement.executeQuery();

        if (rs.next()) {
            System.out.println("Seminar courses taken: " + rs.getInt("seminarcourses"));
            System.out.println("Math credits taken: " + rs.getDouble("mathcredits"));
            System.out.println("Research credits taken: " + rs.getDouble("researchcredits"));
            System.out.println("Total credits taken: " + rs.getDouble("totalcredits"));
            System.out.println("Fulfills the requirements for graduation: " + rs.getBoolean("cangraduate"));
            System.out.println("-------------------------------------");
        }
        rs.close();
        statement.close();
    }

    /* Register: Given a student id number and a course code, this function
     * should try to register the student for that course.
     */
    static void registerStudent(Connection conn, String student, String course)
            throws SQLException
    {
        PreparedStatement registerStatement = conn.prepareStatement("INSERT INTO Registrations VALUES (?, ?);");
        registerStatement.setString(1, student);
        registerStatement.setString(2, course);

        try {
            registerStatement.executeUpdate();
            PreparedStatement checkStatement = conn.prepareStatement("SELECT status FROM Registrations WHERE student = ? AND course = ?;");
            checkStatement.setString(1, student);
            checkStatement.setString(2, course);

            ResultSet rs = checkStatement.executeQuery();
            if (rs.next()) {
                System.out.println(rs.getString(1).equals("registered") ?
                        "You are now successfully registered to course " + course + "!" :
                        "Course " + course + " is full, you are put in the waiting list.");
            }
            checkStatement.close();
        } catch (PSQLException e) {
            System.out.println("Failed to register to course " + course + ".");
        } finally {
            registerStatement.close();
        }
    }

    /* Unregister: Given a student id number and a course code, this function
     * should unregister the student from that course.
     */
    static void unregisterStudent(Connection conn, String student, String course)
            throws SQLException
    {
        PreparedStatement unregisterStatement = conn.prepareStatement("DELETE FROM Registrations WHERE student = ? AND course = ?");
        unregisterStatement.setString(1, student);
        unregisterStatement.setString(2, course);

        unregisterStatement.executeUpdate();
        unregisterStatement.close();

        System.out.println("You have successfully been unregistered or removed from waiting list for course " + course + "!");
    }
}
