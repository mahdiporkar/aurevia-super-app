package com.aurevia.authz.policy;
import static org.assertj.core.api.Assertions.*;import java.util.*;import org.junit.jupiter.api.Test;
class OperationalRulesTest {
  record Employee(String id,String orgUnit) implements OperationalRules.OrgScoped{}
  final OperationalRules rules=new OperationalRules();
  @Test void filtersHrRowsServerSide(){assertThat(rules.enforceOrgScope(List.of(new Employee("1","tehran"),new Employee("2","shiraz")),"tehran")).extracting(Employee::id).containsExactly("1");}
  @Test void makerCannotApproveOwnPayment(){assertThatThrownBy(()->rules.enforcePaymentApproval("u1","u1")).isInstanceOf(OperationalRules.SeparationOfDutiesException.class);}
}
