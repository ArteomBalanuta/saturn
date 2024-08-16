package org.saturn;

import org.junit.jupiter.api.Test;
import org.saturn.app.util.SeparatorFormatter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SeparatorFormatterTest {
    // Arrange
    List<String> list = new ArrayList<>();
    {
        list.add(null);
        list.add("test");
        list.add("test2");
        list.add("test3");
        list.add("test4");
        list.add(null);
    }
    
    @Test
    public void testGetFirst() {
        // Arrange, Act
        SeparatorFormatter formatter = new SeparatorFormatter();
        Object actual = formatter.getFirst(list);
        
        // Assert
        assertEquals("test", actual);
    }
    
    @Test
    public void testGetLast() {
        // Arrange, Act
        SeparatorFormatter formatter = new SeparatorFormatter();
        Object actual = formatter.getLast(list);
        
        // Assert
        assertEquals("test4", actual);
    }

    
    @Test
    public void testAddSeparator() {
        // Arrange
        List<String> expected = new ArrayList<>();
        expected.add(null);
        expected.add("test,");
        expected.add("test2,");
        expected.add("test3,");
        expected.add("test4");
        expected.add(null);
        
        // Act
        SeparatorFormatter formatter = new SeparatorFormatter();
        List actual = formatter.addSeparator(list, ',');
    
        // Assert
        assertEquals(expected, actual);
    }
}
