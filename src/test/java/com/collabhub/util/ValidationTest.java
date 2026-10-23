package com.collabhub.util;

import static org.junit.Assert.*;

import java.util.Collections;
import org.junit.Test;

public class ValidationTest {
  @Test
  public void notificationPreviewRespectsUnicodeCharacters() {
    String text = String.join("", Collections.nCopies(700, "🙂"));
    String preview = Validation.preview(text, 600);
    assertEquals(600, preview.codePointCount(0, preview.length()));
    assertTrue(preview.endsWith("…"));
    assertEquals("short", Validation.preview("short", 600));
  }

  @Test
  public void bcryptPasswordLimitUsesUtf8Bytes() {
    String max = String.join("", Collections.nCopies(72, "p"));
    assertTrue(Validation.validPasswordLength(max));
    assertFalse(Validation.validPasswordLength(max + "x"));
    assertFalse(Validation.validPasswordLength(String.join("", Collections.nCopies(37, "é"))));
    assertFalse(Validation.validPasswordLength(null));
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidCalendarDatesAreRejected() {
    Validation.date("2026-02-30");
  }
}
