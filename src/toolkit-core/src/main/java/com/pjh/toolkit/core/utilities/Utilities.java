package com.pjh.toolkit.core.utilities;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utilities {

  private Utilities() {}

  public static Object getByPath(Object root, String path) {
    if (root == null) {
      throw new IllegalArgumentException("root cannot be null");
    }
    if (path == null || path.trim().isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    Object current = root;
    String[] segments = path.split("\\.");

    for (String segment : segments) {
      if (current == null) return null;

      Segment s = parseSegment(segment);

      // Read property
      Object next = readProperty(current, s.propName);
      if (next == null) return null;

      // Optional index
      if (s.index == null) {
        current = next;
        continue;
      }

      Integer idx = s.index;

      if (next instanceof List<?> list) {
        if (idx < 0 || idx >= list.size()) return null;
        current = list.get(idx);
      } else if (next.getClass().isArray()) {
        int len = Array.getLength(next);
        if (idx < 0 || idx >= len) return null;
        current = Array.get(next, idx);
      } else {
        return null;
      }
    }

    return current;
  }

  public static boolean addToPath(Object root, String path, Object value) {
    if (root == null) {
      throw new IllegalArgumentException("root cannot be null");
    }
    if (path == null || path.trim().isEmpty()) {
      throw new IllegalArgumentException("path cannot be empty");
    }

    Object current = root;
    String[] segments = path.split("\\.");

    for (int i = 0; i < segments.length; i++) {
      boolean isLast = (i == segments.length - 1);
      Segment s = parseSegment(segments[i]);

      if (s.index == null) {
        // Simple property
        if (isLast) {
          return writeProperty(current, s.propName, value);
        }

        Object next = readProperty(current, s.propName);
        if (next == null) {
          Class<?> propType = resolvePropertyType(current.getClass(), s.propName);
          if (propType == null) return false;

          Object created = instantiate(propType);
          if (created == null) return false;

          if (!writeProperty(current, s.propName, created)) return false;
          next = created;
        }

        current = next;
        continue;
      }

      // Indexed segment: must be list/array
      Integer idx = s.index;
      if (idx == null || idx < 0) return false;

      Object listOrArray = readProperty(current, s.propName);

      // If null, create list instance when possible
      if (listOrArray == null) {
        Class<?> propType = resolvePropertyType(current.getClass(), s.propName);
        if (propType == null) return false;

        Object created = createListInstance(propType);
        if (created == null) return false;

        if (!writeProperty(current, s.propName, created)) return false;
        listOrArray = created;
      }

      // Arrays: we don't auto-resize arrays; only set if within bounds
      if (listOrArray != null && listOrArray.getClass().isArray()) {
        int len = Array.getLength(listOrArray);
        if (idx >= len) return false;

        if (isLast) {
          Array.set(listOrArray, idx, value);
          return true;
        }

        Object element = Array.get(listOrArray, idx);
        if (element == null) {
          Class<?> elementType = listOrArray.getClass().getComponentType();
          Object created = instantiate(elementType);
          if (created == null) return false;
          Array.set(listOrArray, idx, created);
          element = created;
        }

        current = element;
        continue;
      }

      // Lists: grow like your .NET EnsureListSize (fills with nulls)
      if (!(listOrArray instanceof List<?>)) return false;
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) listOrArray;

      ensureListSize(list, idx);

      if (isLast) {
        list.set(idx, value);
        return true;
      }

      Object element = list.get(idx);
      if (element == null) {
        // best-effort element type resolution (generic info is often erased)
        Class<?> elementType = resolveListElementType(current.getClass(), s.propName);
        if (elementType == null) elementType = Object.class;

        Object created = instantiate(elementType);
        if (created == null) {
          // If we can't instantiate, we can still navigate only if caller sets last segment;
          // but here we're not last, so fail.
          return false;
        }

        list.set(idx, created);
        element = created;
      }

      current = element;
    }

    return false;
  }

  private static final Pattern SEGMENT_PATTERN = Pattern.compile("^([^\\[]+)(?:\\[(\\d+)\\])?$");

  private static Segment parseSegment(String segment) {
    Matcher m = SEGMENT_PATTERN.matcher(segment);
    if (!m.matches()) {
      // Treat as property name only (no index)
      return new Segment(segment, null);
    }
    String prop = m.group(1);
    String idx = m.group(2);
    return new Segment(prop, idx == null ? null : Integer.parseInt(idx));
  }

  private record Segment(String propName, Integer index) {}

  private static Object readProperty(Object target, String propName) {
    if (target == null) return null;
    if (propName == null || propName.isBlank()) return null;

    Class<?> type = target.getClass();

    // Try JavaBean getters: getX / isX
    Method getter = findGetter(type, propName);
    if (getter != null) {
      try {
        if (!getter.canAccess(target)) getter.setAccessible(true);
        return getter.invoke(target);
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }

    // Fallback to field
    Field field = findField(type, propName);
    if (field != null) {
      try {
        if (!field.canAccess(target)) field.setAccessible(true);
        return field.get(target);
      } catch (ReflectiveOperationException ignored) {
        return null;
      }
    }

    return null;
  }

  private static boolean writeProperty(Object target, String propName, Object value) {
    if (target == null) return false;
    if (propName == null || propName.isBlank()) return false;

    Class<?> type = target.getClass();

    // Try JavaBean setter: setX
    Method setter = findSetter(type, propName, value);
    if (setter != null) {
      try {
        if (!setter.canAccess(target)) setter.setAccessible(true);
        setter.invoke(target, value);
        return true;
      } catch (ReflectiveOperationException ignored) {
        // fall through
      }
    }

    // Fallback to field
    Field field = findField(type, propName);
    if (field != null) {
      try {
        if (!field.canAccess(target)) field.setAccessible(true);

        // If field is primitive and value is null -> cannot set
        if (field.getType().isPrimitive() && value == null) return false;

        field.set(target, value);
        return true;
      } catch (ReflectiveOperationException ignored) {
        return false;
      }
    }

    return false;
  }

  private static Method findGetter(Class<?> type, String propName) {
    String suffix = capitalize(propName);

    // getX()
    Method m = findNoArgMethod(type, "get" + suffix);
    if (m != null) return m;

    // isX() (boolean)
    m = findNoArgMethod(type, "is" + suffix);
    if (m != null) return m;

    return null;
  }

  private static Method findNoArgMethod(Class<?> type, String name) {
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      // try declared methods (non-public)
      try {
        return type.getDeclaredMethod(name);
      } catch (NoSuchMethodException ignored2) {
        return null;
      }
    }
  }

  private static Method findSetter(Class<?> type, String propName, Object value) {
    String suffix = capitalize(propName);
    String name = "set" + suffix;

    // Try any setter named setX with one parameter, pick best match.
    Method best = null;
    for (Method m : type.getMethods()) {
      if (!m.getName().equals(name)) continue;
      if (m.getParameterCount() != 1) continue;
      best = pickBetterSetter(best, m, value);
    }
    if (best != null) return best;

    for (Method m : type.getDeclaredMethods()) {
      if (!m.getName().equals(name)) continue;
      if (m.getParameterCount() != 1) continue;
      best = pickBetterSetter(best, m, value);
    }

    return best;
  }

  private static Method pickBetterSetter(Method current, Method candidate, Object value) {
    Class<?> pt = candidate.getParameterTypes()[0];

    if (value == null) {
      // Prefer non-primitive (can accept null)
      if (pt.isPrimitive()) return current;
      return candidate;
    }

    Class<?> vt = value.getClass();
    boolean candidateOk = pt.isAssignableFrom(vt) || isBoxingCompatible(pt, vt);
    if (!candidateOk) return current;

    if (current == null) return candidate;

    // Prefer "closer" parameter type
    Class<?> cpt = current.getParameterTypes()[0];
    if (cpt.equals(Object.class) && !pt.equals(Object.class)) return candidate;

    return current;
  }

  private static boolean isBoxingCompatible(Class<?> paramType, Class<?> valueType) {
    if (!paramType.isPrimitive()) return false;
    return (paramType == boolean.class && valueType == Boolean.class)
      || (paramType == int.class && valueType == Integer.class)
      || (paramType == long.class && valueType == Long.class)
      || (paramType == double.class && valueType == Double.class)
      || (paramType == float.class && valueType == Float.class)
      || (paramType == short.class && valueType == Short.class)
      || (paramType == byte.class && valueType == Byte.class)
      || (paramType == char.class && valueType == Character.class);
  }

  private static Field findField(Class<?> type, String propName) {
    // Search up the hierarchy
    Class<?> current = type;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField(propName);
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  private static Class<?> resolvePropertyType(Class<?> owner, String propName) {
    // Prefer getter return type
    Method getter = findGetter(owner, propName);
    if (getter != null) return getter.getReturnType();

    // Fallback to field type
    Field field = findField(owner, propName);
    if (field != null) return field.getType();

    return null;
  }

  private static Class<?> resolveListElementType(Class<?> owner, String propName) {
    // Try field generic parameter: List<T>
    Field field = findField(owner, propName);
    if (field != null) {
      Type gt = field.getGenericType();
      Class<?> t = resolveFirstGenericArgAsClass(gt);
      if (t != null) return t;
    }

    // Try getter generic return type
    Method getter = findGetter(owner, propName);
    if (getter != null) {
      Type gt = getter.getGenericReturnType();
      Class<?> t = resolveFirstGenericArgAsClass(gt);
      if (t != null) return t;
    }

    return null;
  }

  private static Class<?> resolveFirstGenericArgAsClass(Type type) {
    if (type instanceof ParameterizedType pt) {
      Type[] args = pt.getActualTypeArguments();
      if (args.length > 0) {
        Type a = args[0];
        if (a instanceof Class<?> c) return c;
        if (a instanceof ParameterizedType ppt && ppt.getRawType() instanceof Class<?> rc) return rc;
      }
    }
    return null;
  }

  private static Object instantiate(Class<?> type) {
    if (type == null) return null;

    // Can't instantiate interfaces/abstracts
    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      // Special-case common interfaces
      if (type == List.class) return new ArrayList<>();
      if (type == Map.class) return new HashMap<>();
      return null;
    }

    try {
      Constructor<?> ctor = type.getDeclaredConstructor();
      if (!ctor.canAccess(null)) ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private static Object createListInstance(Class<?> type) {
    if (type == null) return null;

    if (type.isArray()) {
      // we can't know target size here; arrays are fixed — caller path would need existing array
      return null;
    }

    if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
      if (List.class.isAssignableFrom(type)) return new ArrayList<>();
      return null;
    }

    if (List.class.isAssignableFrom(type)) {
      Object created = instantiate(type);
      if (created != null) return created;
      // fallback if it can't be instantiated
      return new ArrayList<>();
    }

    return null;
  }

  private static void ensureListSize(List<Object> list, int index) {
    while (list.size() <= index) {
      list.add(null);
    }
  }

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    if (s.length() == 1) return s.substring(0, 1).toUpperCase(Locale.ROOT);
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
  }
}
