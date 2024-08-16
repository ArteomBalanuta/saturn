package org.saturn.app.util;

import org.junit.jupiter.api.Test;

import static org.saturn.app.command.impl.user.HelpUserCommandImpl.*;

/* TODO: add assertion */
class UtilTest {

  @Test
  void testAlignHelp() {
    String prefix = "-";

    String header = String.format(helpHeader, prefix);
    String payload = userCommands;
    String examples = String.format(helpExamples, prefix, prefix, prefix, prefix, prefix, prefix);

    String input = header + payload + examples;

    String actual = Util.alignWithWhiteSpace(payload, "-", "\u2009", false);

    System.out.println(input);
    System.out.println("####");
    System.out.println(
        header.replace("\\n", "\n") + actual.replace("\\n", "\n") + examples.replace("\\n", "\n"));
  }
}
