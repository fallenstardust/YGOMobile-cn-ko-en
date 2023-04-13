package net.kk.xml;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

class ReflectUtils {

    public static boolean isNormal(Class<?> type){
        if (type == null || type.isEnum()) {
            return true;
        }
        return boolean.class == type || Boolean.class == type
                || int.class == type || Integer.class == type
                || long.class == type || Long.class == type
                || short.class == type || Short.class == type
                || byte.class == type || Byte.class == type
                || double.class == type || Double.class == type
                || float.class == type || Float.class == type
                || char.class == type || Character.class == type
                || String.class == type;
    }


    public static Object[] getDefault(Class<?>[] classes) {
        Object[] objects = new Object[classes.length];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = getDefault(classes[i]);
        }
        return objects;
    }

    public static Class<?> getListClass(Field field) {
        if (field.getType().isAssignableFrom(List.class)) //【2】
        {
            Type fc = field.getGenericType(); // 关键的地方，如果是List类型，得到其Generic的类型
            if (fc instanceof ParameterizedType) // 【3】如果是泛型参数的类型
            {
                ParameterizedType pt = (ParameterizedType) fc;
                return (Class) pt.getActualTypeArguments()[0]; //【4】 得到泛型里的class类型对象。
            }
        }
        return Object.class;
    }

    public static Class<?>[] getMapClass(Field field) {
        if (field.getType().isAssignableFrom(Map.class)) //【2】
        {
            Type fc = field.getGenericType(); // 关键的地方，如果是List类型，得到其Generic的类型
            if (fc instanceof ParameterizedType) // 【3】如果是泛型参数的类型
            {
                ParameterizedType pt = (ParameterizedType) fc;
                return new Class[]{
                        (Class) pt.getActualTypeArguments()[0],
                        (Class) pt.getActualTypeArguments()[1]
                };
            }
        }
        return new Class[]{Object.class, Object.class};
    }

    public static Object getDefault(Class<?> type) {
        if (type == null) {
            return null;
        } else if (type.isPrimitive()) {
            if (boolean.class == type) {
                return Boolean.FALSE;
            } else if (int.class == type) {
                return 0;
            } else if (long.class == type) {
                return (long) 0;
            } else if (short.class == type) {
                return (short) 0;
            } else if (byte.class == type) {
                return (byte) 0;
            } else if (double.class == type) {
                return (double) 0;
            } else if (float.class == type) {
                return (float) 0;
            } else if (char.class == type) {
                return (char) 0;
            }
        }
        return null;
    }

    public static Method findMethod(Class<?> cls, String name, Class<?>... types) {
        Method method = null;
        try {
            method = exactMethod(cls, name, types);
        } catch (NoSuchMethodException e) {
            try {
                method = similarMethod(cls, name, types);
            } catch (NoSuchMethodException e1) {
                throw new RuntimeException(e1);
            }
        }
        return method;
    }

    /**
     * 根据方法名和方法参数得到该方法。
     */
    private static Method exactMethod(Class<?> type, String name, Class<?>[] types)
            throws NoSuchMethodException {

        // 先尝试直接调用
        try {
            return type.getMethod(name, types);
        }

        // 也许这是一个私有方法
        catch (NoSuchMethodException e) {
            do {
                try {
                    return type.getDeclaredMethod(name, types);
                } catch (NoSuchMethodException ignore) {
                }

                type = type.getSuperclass();
            } while (type != null);

            throw new NoSuchMethodException();
        }
    }

    /**
     * 给定方法名和参数，匹配一个最接近的方法
     */
    private static Method similarMethod(Class<?> type, String name, Class<?>[] types)
            throws NoSuchMethodException {
        // 对于公有方法:
        for (Method method : type.getMethods()) {
            if (isSimilarSignature(method, name, types)) {
                return method;
            }
        }
        // 对于私有方法：
        do {
            for (Method method : type.getDeclaredMethods()) {
                if (isSimilarSignature(method, name, types)) {
                    return method;
                }
            }
            type = type.getSuperclass();
        } while (type != null);
        throw new NoSuchMethodException("No similar method " + name
                + " with params " + Arrays.toString(types)
                + " could be found on type " + type + ".");
    }

    private static boolean isSimilarSignature(Method possiblyMatchingMethod,
                                              String desiredMethodName, Class<?>[] desiredParamTypes) {
        return possiblyMatchingMethod.getName().equals(desiredMethodName)
                && match(possiblyMatchingMethod.getParameterTypes(),
                desiredParamTypes);
    }

    private static boolean match(Class<?>[] declaredTypes, Class<?>[] actualTypes) {
        if (declaredTypes.length == actualTypes.length) {
            for (int i = 0; i < actualTypes.length; i++) {
                if (actualTypes[i] == Reflect.NULL.class)
                    continue;

                if (wrapper(declaredTypes[i]).isAssignableFrom(
                        wrapper(actualTypes[i])))
                    continue;

                return false;
            }
            return true;
        } else {
            return false;
        }
    }

    public static Object[] reObjects(Object... args) {
        if (args != null) {
            Object[] news = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Reflect.NULL) {
                    news[i] = null;
                } else {
                    news[i] = args[i];
                }
            }
            return news;
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    public static <T> Collection<T> createCollection(Class<?> pClass, Class<T> rawType) {
        if (SortedSet.class.isAssignableFrom(pClass)) {
            return new TreeSet<T>();
        } else if (EnumSet.class.isAssignableFrom(pClass)) {
            Type type = rawType.getGenericSuperclass();
            if (type instanceof ParameterizedType) {
                Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
                if (elementType instanceof Class) {
                    return (Collection<T>) EnumSet.noneOf((Class) elementType);
                } else {
                    throw new RuntimeException("Invalid EnumSet type: " + type);
                }
            } else {
                throw new RuntimeException("Invalid EnumSet type: " + type.toString());
            }
        } else if (Set.class.isAssignableFrom(pClass)) {
            return new LinkedHashSet<T>();
        } else if (Queue.class.isAssignableFrom(pClass)) {
            return new LinkedList<T>();
        } else {
            return new ArrayList<T>();
        }
    }

    public static <K, V> Map<K, V> createMap(Class<?> rawType, Class<K> key, Class<V> value) {
        if (SortedMap.class.isAssignableFrom(rawType)) {
            return new TreeMap<K, V>();
        } else if (LinkedHashMap.class.isAssignableFrom(rawType)) {
            return new LinkedHashMap<K, V>();
        } else {
            return new HashMap<K, V>();
        }

    }

    public static Class<?> wrapper(Class<?> type) {
        if (type == null) {
            return Object.class;
        } else if (type.isPrimitive()) {
            if (boolean.class == type) {
                return Boolean.class;
            } else if (int.class == type) {
                return Integer.class;
            } else if (long.class == type) {
                return Long.class;
            } else if (short.class == type) {
                return Short.class;
            } else if (byte.class == type) {
                return Byte.class;
            } else if (double.class == type) {
                return Double.class;
            } else if (float.class == type) {
                return Float.class;
            } else if (char.class == type) {
                return Character.class;
            } else if (void.class == type) {
                return Void.class;
            }
        }
        return type;
    }

    public static Class<?>[] warpperClass(Object... values) {
        if (values == null) {
            // 空
            return new Class[0];
        }
        Class<?>[] result = new Class[values.length];
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Reflect.NULL) {
                result[i] = ((Reflect.NULL) value).clsName;
            } else {
                result[i] = value == null ? Object.class : value.getClass();
            }
        }
        return result;
    }

    public static Object wrapperValue(Class<?> type, String object, XmlOptions options) throws Exception {
        String value = object == null ? "" : object;
        value = value.replace("\t", "").replace("\r", "").replace("\n", "");
        if (type == null) {
            return object;
        }

        if (boolean.class == type || Boolean.class == type) {
            return Boolean.parseBoolean(value);
        } else if (int.class == type || Integer.class == type) {
            if (value.trim().length() == 0) {
                return 0;
            }
            return (value.startsWith("0x")) ?
                    Integer.parseInt(value.substring(2), 16) : Integer.parseInt(value);
        } else if (long.class == type || Long.class == type) {
            if (value.trim().length() == 0) {
                return (long) 0;
            }
            return (value.startsWith("0x")) ?
                    Long.parseLong(value.substring(2), 16) : Long.parseLong(value);
        } else if (short.class == type || Short.class == type) {
            if (value.trim().length() == 0) {
                return (short) 0;
            }
            return (value.startsWith("0x")) ?
                    Short.parseShort(value.substring(2), 16) : Short.parseShort(value);
        } else if (byte.class == type || Byte.class == type) {
            if (value.trim().length() == 0) {
                return (byte) 0;
            }
            return value.getBytes()[0];
        } else if (double.class == type || Double.class == type) {
            if (value.trim().length() == 0) {
                return (double) 0;
            }
            return Double.parseDouble(value);
        } else if (float.class == type || Float.class == type) {
            if (value.trim().length() == 0) {
                return (float) 0;
            }
            return Float.parseFloat(value);
        } else if (char.class == type || Character.class == type) {
            if (value.trim().length() == 0) {
                return (char) 0;
            }
            return value.toCharArray()[0];
        } else if (String.class == type) {
            return object == null ? "" : object;
        } else if (type.isEnum()) {
            if (value.trim().length() == 0) {
                return null;
            }
            Object[] vals = Reflect.on(type, options).call(null, "values");
            for (Object o : vals) {
                //isString
                String v = String.valueOf(o);
                //value
                Object i = Reflect.on(o.getClass(), options).call(o, "ordinal");
                if (value.equalsIgnoreCase(v) || value.equals(i)) {
                    return o;
                }
            }
        }
        return object;
    }
}
