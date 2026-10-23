package com.collabhub.filter;

import com.collabhub.dao.UserDao;
import com.collabhub.model.Record;
import com.collabhub.model.User;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;

@WebFilter("/*")
public class SecurityFilter implements Filter {
  public void init(FilterConfig config) {}

  public void destroy() {}

  public static String token() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest r = (HttpServletRequest) request;
    HttpServletResponse s = (HttpServletResponse) response;
    r.setCharacterEncoding("UTF-8");
    s.setCharacterEncoding("UTF-8");
    s.setHeader("X-Content-Type-Options", "nosniff");
    s.setHeader("X-Frame-Options", "DENY");
    s.setHeader("Referrer-Policy", "same-origin");
    s.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'; form-action 'self'; base-uri 'self'");
    String path = r.getServletPath();
    if (path.startsWith("/assets/")) {
      chain.doFilter(r, s);
      return;
    }
    s.setHeader("Cache-Control", "no-store");
    HttpSession session = r.getSession();
    if (session.getAttribute("csrf") == null) session.setAttribute("csrf", token());
    User user = (User) session.getAttribute("user");
    if (user != null) {
      try {
        Record p = new UserDao().profile(user.getId());
        if (p == null || !p.flag("active")) {
          session.invalidate();
          s.sendRedirect(r.getContextPath() + "/login");
          return;
        }
        user = new User(p.id("id"), p.text("name"), p.text("role"));
        session.setAttribute("user", user);
      } catch (Exception e) {
        r.getServletContext().log("Account verification failed", e);
        s.sendError(503);
        return;
      }
    }
    boolean publicPage =
        path.equals("")
            || path.equals("/")
            || path.equals("/home")
            || path.equals("/login")
            || path.equals("/register");
    if (!publicPage && user == null) {
      s.sendRedirect(r.getContextPath() + "/login");
      return;
    }
    if (path.startsWith("/admin") && (user == null || !user.isAdmin())) {
      s.sendError(403);
      return;
    }
    if ("POST".equals(r.getMethod())) {
      String provided;
      try {
        provided = r.getParameter("csrf");
      } catch (IllegalStateException e) {
        s.sendError(413);
        return;
      }
      if (provided == null
          || !java.security.MessageDigest.isEqual(
              provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),
              session
                  .getAttribute("csrf")
                  .toString()
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
        s.sendError(403, "The form expired. Reload the page.");
        return;
      }
    }
    chain.doFilter(r, s);
  }
}
