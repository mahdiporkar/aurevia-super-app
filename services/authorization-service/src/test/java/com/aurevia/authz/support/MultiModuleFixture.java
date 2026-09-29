package com.aurevia.authz.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the shipped contracts from Maven's module working directory or an IDE checkout. */
public final class MultiModuleFixture {
  private MultiModuleFixture() {}
  public static String read(String name) throws IOException {
    for(Path root=Path.of("").toAbsolutePath();root!=null;root=root.getParent()) {
      Path file=root.resolve("apps/mf-test-multi-module").resolve(name);
      if(Files.isRegularFile(file))return Files.readString(file);
    }
    throw new IOException("Cannot locate multi-module demo contract: "+name);
  }
}
