package com.aurevia.authz.directory;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class OuRuleEvaluatorTest {
  private static final String ACCOUNTING="OU=Accounting,OU=Finance,OU=Employees,DC=aurevia,DC=test";
  @Test void exactMatchesOnlyTheCurrentOu(){assertTrue(OuRuleEvaluator.matches(ACCOUNTING,ACCOUNTING,"EXACT"));assertFalse(OuRuleEvaluator.matches(ACCOUNTING,"OU=Finance,OU=Employees,DC=aurevia,DC=test","EXACT"));}
  @Test void subtreeIncludesDescendantsButNotSiblings(){assertTrue(OuRuleEvaluator.matches(ACCOUNTING,"OU=Finance,OU=Employees,DC=aurevia,DC=test","SUBTREE"));assertFalse(OuRuleEvaluator.matches(ACCOUNTING,"OU=Sales,OU=Employees,DC=aurevia,DC=test","SUBTREE"));}
  @Test void anyOfAndAllOfAreExplicitAndEmptyRulesNeverMatch(){assertTrue(OuRuleEvaluator.combine(List.of(false,true),"ANY_OF"));assertFalse(OuRuleEvaluator.combine(List.of(false,true),"ALL_OF"));assertTrue(OuRuleEvaluator.combine(List.of(true,true),"ALL_OF"));assertFalse(OuRuleEvaluator.combine(List.of(),"ANY_OF"));}
}
