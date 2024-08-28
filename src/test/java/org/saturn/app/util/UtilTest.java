package org.saturn.app.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/* TODO: fix */
/*
class UtilTest {


    @Test
    void testAlign() {
        String input =
                "abc:1\\n" +
                "abcdefgh:1\\n" +
                "abc:2\\n" +
                "abc123:3\\n" +
                "a:4\\n";

        String expected = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;abc:123123\\nabcdefgh:123123\\n&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;abc:123123\\n&nbsp;abc 123:123123\\n&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;a:123123\\n";

        String actual = Util.alignWithWhiteSpace(input);

        assertEquals(expected, actual);
    }

    @Test
    void testAlignWithValues() {
        String input =
                        "abc:123123\\n" +
                        "abcdefgh:123123\\n" +
                        "abc:123123\\n" +
                        "abc 123:123123\\n" +
                        "a:123123\\n";

        String expected =
                        "abc&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:123123\\n" +
                        "abcdefgh:123123\\n" +
                        "abc&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:123123\\n" +
                        "abc 123&nbsp;:123123\\n" +
                        "a&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;:123123\\n";

        String actual = Util.alignWithWhiteSpace(input);

        assertEquals(expected, actual);
    }

    @Test
    void testRealCase(){
        String input =
                "Temperature: 25.9 °C\n" +
                "Feels temp: 26.1 °C\n" +
                "&nbsp;&nbsp;&nbsp; \n" +
                "Wind speed : 8.6 km/h\n" +
                "Pressure surface: 1007.3 hPa\n" +
                "&nbsp;&nbsp;&nbsp; \n" +
                "Pressure sea level: 1014.5 hPa";

        String expected =
                "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Temperature: 25.9 °C\\n&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Feels temp: 26.1 °C\\n&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Wind speed : 8.6 km/h\\n&nbsp;&nbsp;Pressure surface: 1007.3 hPa\\nPressure sea level: 1014.5 hPa\\n";

        String actual = Util.alignWithWhiteSpace(input);

        System.out.println(input);
        System.out.println("####");
        System.out.println(actual.replace("\\n","\n"));

        assertEquals(expected, actual);
    }

}
*/