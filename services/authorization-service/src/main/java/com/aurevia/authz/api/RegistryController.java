package com.aurevia.authz.api;
import static com.aurevia.authz.api.dto.PanelDtos.*;
import static com.aurevia.authz.registry.PanelModels.*;
import com.aurevia.authz.registry.PanelAdministrationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/v1/registry")
public class RegistryController {
 private final PanelAdministrationService service;
 public RegistryController(PanelAdministrationService service){this.service=service;}
 @GetMapping("/panels") public List<PanelView> panels(){return service.panels();}
 @PostMapping("/panels") @ResponseStatus(HttpStatus.CREATED)
 public MutationResult create(@Valid @RequestBody PanelRequest request,@RequestHeader("X-Actor") String actor){return service.create(request.toCommand(),actor);}
 @PutMapping("/panels/{id}")
 public MutationResult update(@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody PanelRequest request,@RequestHeader("X-Actor") String actor){return service.update(id,version,request.toCommand(),actor);}
 @DeleteMapping("/panels/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void archive(@PathVariable UUID id,@RequestParam long version,@RequestHeader("X-Actor") String actor){service.archive(id,version,actor);}
 @GetMapping("/audit") public List<AuditView> audit(@RequestParam(defaultValue="100") int limit){return service.audit(limit);}
}
