/*
 * TestResultTest.java
 * NetBeans JUnit based test
 *
 * Created on March 13, 2003, 11:41 AM
 */

package org.ensembl.healthcheck;

import junit.framework.*;

/**
 *
 * @author glenn
 */
public class TestResultTest extends TestCase {
  
  private TestResult tr1, tr2, tr3, tr4;
  
  public TestResultTest(java.lang.String testName) {
    super(testName);
  }
  
  public static void main(java.lang.String[] args) {
    junit.textui.TestRunner.run(suite());
  }
  
  public static Test suite() {
    TestSuite suite = new TestSuite(TestResultTest.class);
    
    return suite;
  }
  
  public void setUp() {
    
    tr1 = new TestResult("True no message",  true);
    tr2 = new TestResult("False no message", false);
    tr3 = new TestResult("True, message",    true,  "message");
    tr4 = new TestResult("False, message",   false, "message");
    
  }
  
  /** Test of setResult method, of class org.ensembl.healthcheck.TestResult. */
  public void testSetResult() {
    System.out.println("testSetResult");
    
    tr1.setResult(false);
    assertTrue(!tr1.getResult());
    
  }
  
  /** Test of getResult method, of class org.ensembl.healthcheck.TestResult. */
  public void testGetResult() {
    System.out.println("testGetResult");
    
    assertTrue(!tr2.getResult());
    
  }
  
  /** Test of getMessage method, of class org.ensembl.healthcheck.TestResult. */
  public void testGetMessage() {
    System.out.println("testGetMessage");
    
    assertEquals(tr3.getMessage(), "message");
    assertEquals(tr4.getMessage(), "message");

  }
  
  /** Test of setMessage method, of class org.ensembl.healthcheck.TestResult. */
  public void testSetMessage() {
    System.out.println("testSetMessage");
    
    tr1.setMessage("new message");
    assertEquals(tr1.getMessage(), "new message");
    
  }
  
  /** Test of getName method, of class org.ensembl.healthcheck.TestResult. */
  public void testGetName() {
    System.out.println("testGetName");
    
    assertEquals(tr1.getName(), "True no message");
    
  }
  
  /** Test of setName method, of class org.ensembl.healthcheck.TestResult. */
  public void testSetName() {
    
    tr2.setName("new name");
    assertEquals(tr2.getName(), "new name");
    
  }
  
}
