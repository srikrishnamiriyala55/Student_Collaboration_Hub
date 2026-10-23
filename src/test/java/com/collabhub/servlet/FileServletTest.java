package com.collabhub.servlet;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Test;

public class FileServletTest {
  @Test
  public void standaloneServerHasAUsableDefaultUploadDirectory() throws Exception {
    Assume.assumeTrue(System.getenv("UPLOAD_DIR") == null);
    String previous = System.getProperty("catalina.base");
    try {
      System.clearProperty("catalina.base");
      Method root = FileServlet.class.getDeclaredMethod("root");
      root.setAccessible(true);
      Path directory = (Path) root.invoke(new FileServlet());
      assertTrue(directory.isAbsolute());
      assertTrue(java.nio.file.Files.isDirectory(directory));
      assertTrue(directory.endsWith(java.nio.file.Paths.get("runtime", "uploads")));
    } finally {
      if (previous == null) System.clearProperty("catalina.base");
      else System.setProperty("catalina.base", previous);
    }
  }
}
