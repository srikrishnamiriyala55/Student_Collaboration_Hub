package com.collabhub;

import java.io.File;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

/** Optional local development runner. Production/Eclipse deployment uses the WAR. */
public final class DevServer {
  public static Tomcat start(int port, String base) throws Exception {
    Tomcat tomcat = new Tomcat();
    tomcat.setBaseDir(new File(base).getAbsolutePath());
    tomcat.setPort(port);
    tomcat.getConnector().setProperty("address", "127.0.0.1");
    Context context =
        tomcat.addWebapp(
            "/Student_Collaboration_Hub", new File("src/main/webapp").getAbsolutePath());
    context.setParentClassLoader(DevServer.class.getClassLoader());
    org.apache.catalina.webresources.StandardRoot resources =
        new org.apache.catalina.webresources.StandardRoot(context);
    resources.addPreResources(
        new org.apache.catalina.webresources.DirResourceSet(
            resources, "/WEB-INF/classes", new File("target/classes").getAbsolutePath(), "/"));
    context.setResources(resources);
    org.apache.tomcat.util.scan.StandardJarScanner scanner =
        (org.apache.tomcat.util.scan.StandardJarScanner) context.getJarScanner();
    scanner.setScanClassPath(true);
    tomcat.start();
    if (!tomcat.getConnector().getState().isAvailable() || !context.getState().isAvailable()) {
      tomcat.stop();
      tomcat.destroy();
      throw new IllegalStateException("Application startup failed. Check the HTTP port and database initialization logs.");
    }
    return tomcat;
  }

  public static void main(String[] args) throws Exception {
    Tomcat t = start(args.length == 0 ? 8080 : Integer.parseInt(args[0]), "runtime/tomcat");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    t.stop();
                    t.destroy();
                  } catch (Exception ignored) {
                  }
                }));
    System.out.println(
        "CollabHub: http://localhost:"
            + (args.length == 0 ? 8080 : args[0])
            + "/Student_Collaboration_Hub/");
    t.getServer().await();
  }
}
