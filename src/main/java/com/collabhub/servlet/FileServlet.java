package com.collabhub.servlet;

import com.collabhub.dao.*;
import com.collabhub.model.Record;
import com.collabhub.model.User;
import com.collabhub.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;

@WebServlet("/files")
@MultipartConfig(maxFileSize = 10485760, maxRequestSize = 11000000, fileSizeThreshold = 0)
public class FileServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private Path root() throws IOException {
    String configured = System.getenv("UPLOAD_DIR");
    Path p =
        configured == null
            ? Paths.get(System.getProperty("catalina.base"), "collabhub-uploads")
            : Paths.get(configured);
    p = p.toAbsolutePath().normalize();
    Files.createDirectories(p);
    return p;
  }

  private User user(HttpServletRequest r) {
    return (User) r.getSession().getAttribute("user");
  }

  protected void doPost(HttpServletRequest r, HttpServletResponse s)
      throws IOException, ServletException {
    Path saved = null;
    long project = 0;
    try {
      project = Validation.id(r.getParameter("projectId"));
      try (java.sql.Connection c = Db.open()) {
        ProjectDao.authorize(c, project, user(r), false);
        Validation.require(
            ProjectDao.member(c, project, user(r).getId()), "Team membership required.");
      }
      Part part = r.getPart("file");
      Validation.require(part != null && part.getSize() > 0, "Choose a nonempty file.");
      Validation.require(part.getSize() <= 10485760, "Files must be 10 MB or smaller.");
      String name = part.getSubmittedFileName();
      Validation.require(name != null, "Invalid file name.");
      name = name.replace('\\', '/');
      name = name.substring(name.lastIndexOf('/') + 1);
      Validation.require(
          name.length() <= 200 && name.matches("[\\p{L}\\p{N} ._()\\-]+"),
          "Use a simple file name.");
      int dot = name.lastIndexOf('.');
      String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
      Map<String, String> types = new HashMap<>();
      types.put("pdf", "application/pdf");
      types.put("png", "image/png");
      types.put("jpg", "image/jpeg");
      types.put("jpeg", "image/jpeg");
      types.put("txt", "text/plain");
      types.put("csv", "text/csv");
      String type = types.get(ext);
      Validation.require(type != null, "Allowed files: PDF, PNG, JPG, TXT, CSV.");
      String supplied = part.getContentType();
      Validation.require(
          type.equals(supplied)
              || (ext.equals("csv") && "application/vnd.ms-excel".equals(supplied)),
          "File content type does not match its extension.");
      byte[] bytes;
      try (InputStream in = part.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        bytes = out.toByteArray();
      }
      validateContent(ext, bytes);
      String stored = UUID.randomUUID().toString() + "." + ext;
      saved = root().resolve(stored);
      Files.write(saved, bytes, StandardOpenOption.CREATE_NEW);
      new FileDao().add(user(r), project, name, stored, type, bytes.length);
      saved = null;
      r.getSession().setAttribute("success", "File uploaded.");
    } catch (IllegalArgumentException | IllegalStateException e) {
      r.getSession().setAttribute("error", e.getMessage());
    } catch (Exception e) {
      getServletContext().log("Upload failed", e);
      r.getSession().setAttribute("error", "Unable to upload this file.");
    } finally {
      if (saved != null) Files.deleteIfExists(saved);
    }
    s.sendRedirect(
        r.getContextPath() + (project > 0 ? "/project-details?id=" + project : "/dashboard"));
  }

  private void validateContent(String ext, byte[] b) {
    if (ext.equals("pdf"))
      Validation.require(
          b.length >= 5
              && new String(b, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"),
          "Invalid PDF content.");
    else if (ext.equals("png"))
      Validation.require(
          b.length >= 8
              && (b[0] & 255) == 137
              && b[1] == 80
              && b[2] == 78
              && b[3] == 71
              && b[4] == 13
              && b[5] == 10
              && b[6] == 26
              && b[7] == 10,
          "Invalid PNG content.");
    else if (ext.equals("jpg") || ext.equals("jpeg"))
      Validation.require(
          b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255,
          "Invalid JPEG content.");
    else {
      for (byte value : b)
        Validation.require(value != 0, "Binary data is not permitted in text files.");
      try {
        java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b));
      } catch (java.nio.charset.CharacterCodingException e) {
        throw new IllegalArgumentException("Text files must use UTF-8 encoding.");
      }
    }
  }

  protected void doGet(HttpServletRequest r, HttpServletResponse s) throws IOException {
    try {
      Record f = new FileDao().get(user(r), Validation.id(r.getParameter("id")));
      Path base = root(), file = base.resolve(f.text("stored_name")).normalize();
      Validation.require(
          file.getParent().equals(base) && Files.isRegularFile(file) && !Files.isSymbolicLink(file),
          "File not found.");
      s.setContentType(f.text("content_type"));
      s.setHeader(
          "Content-Disposition",
          "attachment; filename=\"download\"; filename*=UTF-8''"
              + java.net.URLEncoder.encode(f.text("original_name"), "UTF-8").replace("+", "%20"));
      s.setContentLengthLong(Files.size(file));
      Files.copy(file, s.getOutputStream());
    } catch (IllegalArgumentException e) {
      s.sendError(403, "File not found or access denied.");
    } catch (Exception e) {
      getServletContext().log("Download failed", e);
      s.sendError(503);
    }
  }
}
