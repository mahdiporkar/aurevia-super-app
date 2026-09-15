package com.aurevia.authz.policy;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OperationalRules {
  public <T extends OrgScoped> List<T> enforceOrgScope(List<T> rows,String subjectOrgUnit){ return rows.stream().filter(r->r.orgUnit().equals(subjectOrgUnit)).toList(); }
  public void enforcePaymentApproval(String makerId,String approverId){ if(makerId.equals(approverId))throw new SeparationOfDutiesException(); }
  public interface OrgScoped { String orgUnit(); }
  public static class SeparationOfDutiesException extends RuntimeException { public SeparationOfDutiesException(){super("Payment maker cannot approve the same payment");} }
}
