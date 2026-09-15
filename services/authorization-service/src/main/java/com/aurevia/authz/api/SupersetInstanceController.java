package com.aurevia.authz.api;

import static com.aurevia.authz.api.dto.SupersetInstanceDtos.*;

import com.aurevia.authz.superset.SupersetInstanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Control-plane HTTP adapter for public/operation Superset instances and mappings. */
@RestController
@RequestMapping("/internal/v1/registry/superset-instances")
public final class SupersetInstanceController {
  private final SupersetInstanceService instances;

  public SupersetInstanceController(SupersetInstanceService instances) {
    this.instances=instances;
  }

  @GetMapping public List<InstanceView> instances() { return instances.instances(); }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VersionResponse create(@Valid @RequestBody InstanceRequest request,
      @RequestHeader("X-Actor") String actor) {
    return instances.create(request,actor);
  }

  @PutMapping("/{id}")
  public VersionResponse update(@PathVariable UUID id,
      @Valid @RequestBody InstanceRequest request,@RequestHeader("X-Actor") String actor) {
    return instances.update(id,request,actor);
  }

  @GetMapping("/mappings")
  public List<MappingView> mappings() { return instances.mappings(); }

  @PostMapping("/mappings")
  @ResponseStatus(HttpStatus.CREATED)
  public IdResponse map(@Valid @RequestBody MappingRequest request,
      @RequestHeader("X-Actor") String actor) {
    return instances.map(request,actor);
  }
}
