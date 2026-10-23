package com.collabhub;

import java.io.File;
import org.apache.jasper.JspC;
import org.junit.Test;

/** Compiles every JSP and its included fragments without connecting to any database. */
public class JspCompilationTest {
  @Test
  public void allJspPagesCompile() {
    JspC compiler = new JspC();
    compiler.setUriroot(new File("src/main/webapp").getAbsolutePath());
    compiler.setOutputDir(new File("target/jsp-validation").getAbsolutePath());
    compiler.setClassPath(System.getProperty("java.class.path"));
    compiler.setCompile(true);
    compiler.setCompilerSourceVM("1.8");
    compiler.setCompilerTargetVM("1.8");
    compiler.setJavaEncoding("UTF-8");
    compiler.setFailOnError(true);
    compiler.execute();
  }
}
