package net.kk.xml;

import net.kk.xml.adapter.XmlConstructorAdapter;
import net.kk.xml.adapter.XmlTextAdapter;
import net.kk.xml.annotations.XmlAttribute;
import net.kk.xml.annotations.XmlElement;
import net.kk.xml.annotations.XmlElementText;
import net.kk.xml.bean.TagObject;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class XmlCore {
    public static final String DEF_ENCODING = "UTF-8";
    protected XmlOptions mOptions;

    public XmlCore(XmlOptions options) {
        mOptions = options;
    }

    protected Reflect on(Object obj) {
        return Reflect.on(obj.getClass(), mOptions);
    }

    protected Reflect on(Class<?> pClass) {
        return Reflect.on(pClass, mOptions);
    }

    protected TagObject make(Object obj) throws Exception {
        if (IXmlElement.class.isInstance(obj)) {
            int pos = on(obj).get(obj, "pos");
            return make(obj.getClass(), pos);
        }
        return make(obj.getClass(), 0);
    }

    protected TagObject make(Class<?> pClass, int pos) {
        XmlElement tag = pClass.getAnnotation(XmlElement.class);
        TagObject tagObject = null;
        if (tag != null) {
            tagObject = new TagObject(tag.value(), tag.namespace(), 0, pos);
        }
        if (tagObject == null) {
            tagObject = new TagObject(pClass.getSimpleName(), null, 0, 0);
        }
        System.out.println(tagObject);
        return tagObject;
    }

    protected String getClassTag(Class<?> cls) {
        if (cls == null) return null;
        XmlElement tag = cls.getAnnotation(XmlElement.class);
        if (tag != null) {
            return tag.value();
        }
        return cls.getSimpleName();
    }

    protected boolean isEmtry(String text) {
        return text == null || text.length() == 0;
    }

    protected boolean isXmlText(AnnotatedElement ae) {
        return ae.getAnnotation(XmlElementText.class) != null;
    }

    protected boolean matchTag(Field field, String name) {
        if (isEmtry(name)) return false;
        XmlElement xmlElement = field.getAnnotation(XmlElement.class);
        if (xmlElement == null) {
            if (mOptions.isIgnoreTagCase()) {
                return field.getName().equalsIgnoreCase(name);
            } else {
                return field.getName().equals(name);
            }
        } else {
            String defname = xmlElement.value();
            if (mOptions.isIgnoreTagCase()) {
                if (name.equalsIgnoreCase(defname)) {
                    return true;
                }
            } else {
                if (name.equals(defname)) {
                    return true;
                }
            }
            String alias = xmlElement.alias();
            if (mOptions.isIgnoreTagCase()) {
                if (name.equalsIgnoreCase(alias)) {
                    return true;
                }
            } else {
                if (name.equals(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean matchAttribute(Field field, String name, String namespace) {
        if (isEmtry(name)) return false;
        XmlAttribute attribute = null;
        if (!isEmtry(namespace)) {
            attribute = field.getAnnotation(XmlAttribute.class);
            if (attribute == null) {
                return false;
            }
            if (!namespace.equals(attribute.namespace())) {
                return false;
            }
        }
        if (attribute == null) {
            attribute = field.getAnnotation(XmlAttribute.class);
        }
        if (attribute == null) {
            if (mOptions.isIgnoreTagCase()) {
                return field.getName().equalsIgnoreCase(name);
            } else {
                return field.getName().equals(name);
            }
        } else {
            String defname = attribute.value();
            if (mOptions.isIgnoreTagCase()) {
                if (name.equalsIgnoreCase(defname)) {
                    return true;
                }
            } else {
                if (name.equals(defname)) {
                    return true;
                }
            }
            String alia = attribute.alias();
            if (mOptions.isIgnoreTagCase()) {
                if (name.equalsIgnoreCase(alia)) {
                    return true;
                }
            } else {
                if (name.equals(alia)) {
                    return true;
                }
            }
            return false;
        }
    }

    protected XmlTextAdapter getTypeAdapter(Class<?> pClass) {
        if (mOptions.getXmlTypeAdapterMap() == null) {
            return null;
        }
        return mOptions.getXmlTypeAdapterMap().get(ReflectUtils.wrapper(pClass));
    }

    protected XmlConstructorAdapter getConstructor(Class<?> pClass) {
        if (mOptions.getXmlConstructorAdapterMap() == null) {
            return null;
        }
        return mOptions.getXmlConstructorAdapterMap().get(ReflectUtils.wrapper(pClass));
    }

    protected <T> T create(Class<T> pClass, Object parent) {
        T o = null;
        XmlConstructorAdapter constructorAdapter = getConstructor(pClass);
        if (constructorAdapter != null) {
            try {
                o = constructorAdapter.create(pClass, parent);
            } catch (Exception e) {

            }
            return o;
        }
        try {
            if (pClass.isMemberClass() && (pClass.getModifiers() & Modifier.STATIC) == 0) {
                //内部类
                o = Reflect.on(pClass, mOptions).create(new Class[]{parent.getClass()}, new Object[]{parent});
            } else {
                o = Reflect.on(pClass, mOptions).create(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return o;
    }
}
