package com.aurevia.authz.directory;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DirectoryDnParserTest {
  @Test void extractsLeafOuAndHierarchyWithoutSplittingEscapedCommas() {
    var parsed=DirectoryDnParser.parseUser("CN=Doe\\, Ali,OU=Accounting Experts,OU=Employees,DC=aurevia,DC=test");
    assertEquals("/Employees/Accounting Experts",parsed.ouPath());
    assertEquals("Accounting Experts",parsed.ouName());
    assertEquals("OU=Accounting Experts,OU=Employees,DC=aurevia,DC=test",parsed.ouDn());
  }
  @Test void exactAndSubtreeHaveDifferentSemantics() {
    String leaf="OU=Accounting,OU=Finance,OU=Employees,DC=aurevia,DC=test";
    assertTrue(DirectoryDnParser.isWithinSubtree(leaf,"OU=Finance,OU=Employees,DC=aurevia,DC=test"));
    assertFalse(DirectoryDnParser.canonicalDn(leaf).equalsIgnoreCase(
        DirectoryDnParser.canonicalDn("OU=Finance,OU=Employees,DC=aurevia,DC=test")));
  }
  @Test void rejectsMalformedOrNonOuUserDn() {
    assertThrows(IllegalArgumentException.class,()->DirectoryDnParser.parseUser("CN=Ali,DC=aurevia,DC=test"));
    assertThrows(IllegalArgumentException.class,()->DirectoryDnParser.parseUser("not=a,valid=dn,"));
    assertThrows(IllegalArgumentException.class,()->DirectoryDnParser.parseUser("CN=Ali\0,OU=Employees,DC=test"));
  }
}
