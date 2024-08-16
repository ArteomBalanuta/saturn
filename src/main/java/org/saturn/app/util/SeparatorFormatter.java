package org.saturn.app.util;

import java.util.List;

public class SeparatorFormatter {
  public static List<String> addSeparator(List<String> list, char separator) {
    int size = list.size();
    if (size == 1 || list.isEmpty()) {
      return list;
    }

    Object last = getLast(list);

    for (Object e : list) {
      if (e == last) {
        break;
      }

      if (e != null) {
        String x = e.toString() + separator;
        int i = list.indexOf(e);
        list.set(i, x);
      }
    }

    return list;
  }

  public static Object getFirst(List<String> list) {
    for (Object element : list) {
      if (element != null) {
        return element;
      }
    }
    return null;
  }

  public static Object getLast(List<String> list) {
    Object value = null;
    int index = list.size() - 1;
    for (; index != 0; index--) {
      if (list.get(index) != null) {
        value = list.get(index);
        break;
      }
    }
    return value;
  }
}
