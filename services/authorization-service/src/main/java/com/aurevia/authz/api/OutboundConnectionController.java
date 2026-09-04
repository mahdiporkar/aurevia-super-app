package com.aurevia.authz.api;
import static com.aurevia.authz.api.dto.OutboundRegistryDtos.*;
import static com.aurevia.authz.outbound.OutboundModels.*;
import com.aurevia.authz.outbound.OutboundRegistryService;
import jakarta.validation.Valid;
import java.util.List;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
/** Approved endpoint registry. Credential values are deliberately outside this boundary. */
@RestController public class OutboundConnectionController {
 private final OutboundRegistryService service;
 public OutboundConnectionController(OutboundRegistryService service){this.service=service;}
 @GetMapping("/internal/v1/registry/outbound-connections") public List<ConnectionView> list(){return service.connections();}
 @GetMapping("/internal/v1/outbound-connections/resolve") public RuntimeConnectionView resolve(@RequestParam("ref") String reference){return service.runtimeConnection(reference);}
 @PostMapping("/internal/v1/registry/outbound-connections") @ResponseStatus(HttpStatus.CREATED)
 public MutationResult create(@Valid @RequestBody ConnectionRequest request,@RequestHeader("X-Actor") String actor){return service.createConnection(request.toCommand(),actor);}
 @PutMapping("/internal/v1/registry/outbound-connections/{id}") public MutationResult update(@PathVariable UUID id,
   @Valid @RequestBody ConnectionRequest request,@RequestHeader("X-Actor") String actor){return service.updateConnection(id,request.toCommand(),actor);}
}
