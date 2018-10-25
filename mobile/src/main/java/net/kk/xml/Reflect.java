package net.kk.xml;

import net.kk.xml.annotations.XmlAttribute;
import net.kk.xml.annotations.XmlElement;
import net.kk.xml.annotations.XmlElementText;
import net.kk.xml.annotations.XmlIgnore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Reflect {
    private Class<?> mClass;
    private XmlOptions options;
    private static final HashMap<String, Reflect> sReflectUtils = new HashMap<>();

    private Reflect(Class<?> pClass, XmlOptions options) {
        this.mClass = pClass;
        this.options = options;
    }

    public static Reflect on(Class<?> pClass, XmlOptions options) {
        Reflect reflect = null;
        synchronized (Reflect.class) {
            reflect = sReflectUtils.get(pClass.getName());
            if (reflect == null) {
                reflect = new Reflect(pClass, options);
                sReflectUtils.put(pClass.getName(), reflect);
            } else {
                if (reflect.options == null) {
                    reflect.makeFields = false;
                    reflect.mFields.clear();
                    reflect.options = options;
                }
            }
        }
        return reflect;
    }

    private volatile boolean makeFields = false;
    private volatile boolean findConstructor = false;
    private final HashMap<String, Field> mFields = new HashMap<>();
    private final HashMap<String, Method> mMethods = new HashMap<>();
    private Constructor<?> mConstructor;
    private Constructor<?> mConstructor2;
    private final List<String> mNULLMethods = new ArrayList<>();

    public <T> T create(Class<?>[] args, Object... objs) throws Exception {
        if (args == null) {
            args = new Class[0];
        }
        Constructor<?> constructor = null;
        if (!findConstructor) {
            findConstructor = true;
            try {
                constructor = mClass.getDeclaredConstructor(args);
                constructor.setAccessible(true);
                mConstructor = constructor;
            } catch (Exception e) {
                int min = Integer.MAX_VALUE;
                for (Constructor<?> con : mClass.getDeclaredConstructors()) {
                    if (args == null || args.length == 0) {
                        //取一个最小参数的构造，
                        if (con.getParameterTypes().length < min) {
                            min = con.getParameterTypes().length;
                            constructor = con;
                        }
                    } else {
                        if (con.getParameterTypes().length == args.length) {
                            constructor = con;
                            break;
                        }
                    }
                }
                if (constructor == null) {
                    //没有默认值的参数
                    throw new RuntimeException("no find default constructor " + mClass);
                }
                constructor.setAccessible(true);
                mConstructor2 = constructor;
            }
        } else {
            if (mConstructor != null) {
                constructor = mConstructor;
            } else if (mConstructor2 != null) {
                constructor = mConstructor2;
            } else {
                throw new RuntimeException("no find default constructor " + mClass);
            }
        }
        if (args == null || args.length == 0) {
            objs = ReflectUtils.getDefault(constructor.getParameterTypes());
            return (T) constructor.newInstance(objs);
        }
        return (T) constructor.newInstance(objs);
    }

    private String getMethodKey(String name, Class<?>[] types) {
        return name + ":" + Arrays.toString(types);
    }

    public boolean isNormal() {
        return ReflectUtils.isNormal(mClass);
    }

    public boolean isArray() {
        return mClass.isArray();
    }

    public boolean isCollection() {
        return Collection.class.isAssignableFrom(mClass);
    }

    public boolean isMap() {
        return Map.class.isAssignableFrom(mClass);
    }

    public Class<?> getType() {
        return mClass;
    }

    private boolean enableField(Field field) {
        if (options != null) {
            //忽略静态变量
            if (options.isIgnoreStatic()) {
                if ((field.getModifiers() & Modifier.STATIC) != 0) {
                    return false;
                }
            }
            //忽略的类
            if (options.isIgnore(field.getType())) {
                return false;
            }
        }
        //主动忽略
        if (field.getAnnotation(XmlIgnore.class) != null) {
            return false;
        }
        XmlAttribute attribute = field.getAnnotation(XmlAttribute.class);
        if (attribute != null) {
            return true;
        }
        XmlElement tag = field.getAnnotation(XmlElement.class);
        if (tag != null) {
            return true;
        }
        XmlElementText innerText = field.getAnnotation(XmlElementText.class);
        if (innerText != null) {
            return true;
        }
        if (options != null) {
            //是否忽略没有标记的元素？
            if (options.isUseNoAnnotation()) {
                return true;
            }
        }
        return false;
    }

    private void findAllFields(Class<?> cls) {
        Field[] fields = cls.getDeclaredFields();
        if (fields != null) {
            for (Field f : fields) {
                if (enableField(f)) {
                    f.setAccessible(true);
                    mFields.put(f.getName(), f);
                }
            }
        }
        if (fields != null) {
            fields = cls.getFields();
            for (Field f : fields) {
                if (enableField(f)) {
                    mFields.put(f.getName(), f);
                }
            }
        }
        Class<?> sup = cls.getSuperclass();
        if (sup != null && sup!=Object.class) {
            findAllFields(sup);
        }
    }

    public Field get(String name) {
        return get(name, false);
    }

    public Collection<Field> getFields() {
        if (!makeFields) {
            findAllFields(mClass);
            makeFields = true;
        }
        return mFields.values();
    }

    public Field get(String name, boolean ignonreCase) {
        Collection<Field> fields = getFields();
        if (fields != null) {
            for (Field field : fields) {
                if (ignonreCase) {
                    if (field.getName().equalsIgnoreCase(name)) {
                        return field;
                    }
                } else {
                    if (field.getName().equals(name)) {
                        return field;
                    }
                }
            }
        }
        return null;
    }

    public <T> T get(Object obj, String name) throws Exception {
        return get(obj, name, null);
    }

    public <T> T get(Object obj, String name, Object def) throws Exception {
        Field field = get(name);
        if (field == null) {
            return (T) def;
        }
        return (T) field.get(obj);
    }

    public void set(Object obj, String name, Object value) throws Exception {
        set(obj, name, value, false);
    }

    public void set(Object obj, String name, Object value, boolean usemethod) throws Exception {
        Field field = get(name);
        if (field != null) {
            field.set(obj, value);
        }
    }

    public <T> T call(Object obj, String name, Object... args) throws Exception {
        Class<?>[] types = ReflectUtils.warpperClass(args);
        String key = getMethodKey(name, types);
        Method method = null;
        synchronized (mMethods) {
            method = mMethods.get(key);
            if (method == null) {
                if (!mNULLMethods.contains(key)) {
                    // 防止重复
                    method = ReflectUtils.findMethod(mClass, name, types);
                    if (method == null) {
                        mNULLMethods.add(key);
                    }
                }
            }
        }
        if (method == null) {
            throw new Exception("no find method " + name);
        }
        args = ReflectUtils.reObjects(args);
        return (T) method.invoke(obj, args);
    }

    public static class NULL {
        public NULL(Class<?> cls) {
            this.clsName = cls;
        }

        public Class<?> clsName;
    }
}
