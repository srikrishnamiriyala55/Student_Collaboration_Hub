package com.collabhub.util;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class DatabaseStartupListener implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent event) {
    try {
      Db.initialize();
      event.getServletContext().log("CollabHub database initialization completed.");
    } catch (Exception e) {
      event.getServletContext().log("CollabHub database initialization failed.", e);
      throw new IllegalStateException(
          "Database startup failed. Verify MySQL is running, credentials are correct, and the account has CREATE privileges.", e);
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {}
}
