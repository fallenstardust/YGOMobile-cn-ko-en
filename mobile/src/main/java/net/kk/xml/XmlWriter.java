package net.kk.xml;

import net.kk.xml.adapter.XmlTextAdapter;
import net.kk.xml.annotations.XmlAttribute;
import net.kk.xml.annotations.XmlElement;
import net.kk.xml.annotations.XmlElementText;
import net.kk.xml.bean.AttributeObject;
import net.kk.xml.bean.TagObject;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class XmlWriter extends XmlCore {
    private static final String NEW_LINE = System.getProperty("line.separator", "\n");
    private final XmlSerializer serializer;

    /***
     * @see org.xmlpull.v1.XmlPullParserFactory newInstance().newSerializer()
     */
    public XmlWriter(XmlSerializer serializer, XmlOptions options) {
        super(options);
        this.serializer = serializer;
    }

    //write xml
    public void write(Object object, OutputStream outputStream, String encoding) throws Exception {
        write(toRootTag(object), outputStream, encoding);
    }

    public void write(TagObject xmlObject, OutputStream outputStream, String encoding) throws Exception {
        serializer.setOutput(outputStream, encoding==null?DEF_ENCODING:encoding);
        serializer.startDocument(encoding, null);
        writeTag(xmlObject, serializer, 1);
        serializer.endDocument();
    }

    private void writeTag(TagObject xmlObject, XmlSerializer serializer, int depth)
            throws IOException, IllegalAccessException {
        if (xmlObject == null || serializer == null) return;
        if (mOptions.isUseSpace()) {
            serializer.text(NEW_LINE);
            writeTab(serializer, depth);
        }
        serializer.startTag(xmlObject.getNamespace(), xmlObject.getName());
        //attribute
        List<AttributeObject> attributeObjects = xmlObject.getAttributes();
        if (attributeObjects != null) {
            for (AttributeObject e : attributeObjects) {
                serializer.attribute(e.getNamespace(), e.getName(), e.getValue());
            }
        }
        //sub tags
        List<TagObject> subTags = xmlObject.getSubTags();
        if (subTags != null) {
            Collections.sort(subTags, TagObject.ASC);
            for (TagObject sub : subTags) {
                writeTag(sub, serializer, depth + 1);
            }
        }
        //text
        if (!isEmtry(xmlObject.getText())) {
            serializer.text(xmlObject.getText());
        }
        if (mOptions.isUseSpace() && subTags != null && subTags.size() > 0) {
            serializer.text(NEW_LINE);
            writeTab(serializer, depth);
        }
        serializer.endTag(xmlObject.getNamespace(), xmlObject.getName());
    }

    private void writeTab(XmlSerializer pXmlSerializer, int depth) throws IOException {
        for (int i = 0; i < depth - 1; i++) {
            pXmlSerializer.text("\t");
        }
    }

    //endregion

    private String toString(Object object) {
        XmlTextAdapter adapter = getTypeAdapter(object.getClass());
        if (adapter != null) {
            return adapter.toString(object.getClass(), object);
        }
        return "" + object;

    }

    TagObject toRootTag(Object object) throws Exception {
        TagObject tagObject;
        if (object == null) {
            return new TagObject("root", null, 0, 0);
        }
        tagObject = make(object);
        fillTag(tagObject, object, null);
        return tagObject;
    }

    private AttributeObject toAttributeObject(Object object, XmlAttribute attribute, Object parent) {
        AttributeObject attributeObject = new AttributeObject(attribute.namespace(), attribute.value());
        attributeObject.setValue(toString(object));
        return attributeObject;
    }

    private List<TagObject> toSubTags(Field field, Object object, Object parent) throws Exception {
        List<TagObject> list = new ArrayList<>();
        Reflect reflect = on(object);
        if (reflect.isArray()) {
            list.addAll(array(field, object, parent));
        } else if (reflect.isCollection()) {
            list.addAll(list(field, object, parent));
        } else if (reflect.isMap()) {
            list.addAll(map(field, object, parent));
        } else {
            TagObject tagObject = toSubTag(field, object, parent);
            list.add(tagObject);
        }
        return list;
    }

    private void fillTag(TagObject tagObject, Object object, Object parent) throws Exception {
        if (object == null) {
            return;
        }
        Reflect reflect = on(object);
        if (reflect.isNormal()) {
            tagObject.setText(toString(object));
        } else {
            boolean writeText = false;
            Collection<Field> fields = reflect.getFields();
            for (Field field : fields) {
                Object val = field.get(object);
                if (val == null) {
                    continue;
                }
                XmlAttribute attribute = field.getAnnotation(XmlAttribute.class);
                if (attribute != null) {
                    tagObject.addAttribute(toAttributeObject(val, attribute, object));
                } else {
                    if (!writeText) {
                        XmlElementText xmlInnerText = field.getAnnotation(XmlElementText.class);
                        if (xmlInnerText != null) {
                            writeText = true;
                            tagObject.setText(toString(val));
                            continue;
                        }
                    }
                    tagObject.addSubTags(toSubTags(field, val, parent));
                }
            }
        }

    }

    private TagObject toSubTag(Field field, Object object, Object parent) throws Exception {
        XmlElement xmlElement = field.getAnnotation(XmlElement.class);
        String name = xmlElement == null ? field.getName() : xmlElement.value();
        String namespace = xmlElement == null ? null : xmlElement.namespace();
        TagObject tagObject = new TagObject(name, namespace, 0, 0);
        fillTag(tagObject, object, parent);
        return tagObject;
    }

    //region list
    private List<TagObject> array(Field field, Object object, Object parent) throws Exception {
        XmlElement xmlElement = field.getAnnotation(XmlElement.class);
        String name = xmlElement == null ? field.getName() : xmlElement.value();
        String namespace = xmlElement == null ? null : xmlElement.namespace();
        String itemName = xmlElement == null ? XmlElement.ITEM : xmlElement.listItem();
        boolean isSameList = mOptions.isSameAsList();
        ArrayList<TagObject> list = new ArrayList<TagObject>();
        TagObject root = new TagObject(name, namespace, 0, 0);
        if (!isSameList) {
            list.add(root);
        }
        if (object != null) {
            int count = Array.getLength(object);
            for (int i = 0; i < count; i++) {
                Object obj = Array.get(object, i);
                if (obj != null) {
                    if (isSameList) {
                        TagObject object1 = new TagObject(name, namespace, 0, i);
                        fillTag(object1, obj, parent);
                        list.add(object1);
                    } else {
                        TagObject object1 = new TagObject(itemName, namespace, 0, i);
                        fillTag(object1, obj, parent);
                        root.addSubTag(object1);
                    }
                }
            }
        }
        return list;
    }

    private List<TagObject> list(Field field, Object object, Object parent) throws Exception {
        XmlElement xmlElement = field.getAnnotation(XmlElement.class);
        String name = xmlElement == null ? field.getName() : xmlElement.value();
        String namespace = xmlElement == null ? null : xmlElement.namespace();
        String itemName = xmlElement == null ? XmlElement.ITEM : xmlElement.listItem();
        boolean isSameList = mOptions.isSameAsList();
        ArrayList<TagObject> list = new ArrayList<TagObject>();
        TagObject root = new TagObject(name, namespace, 0, 0);
        if (!isSameList) {
            list.add(root);
        }
        if (object != null) {
            Object[] objs = on(object.getClass()).call(object, "toArray");
            if (objs != null) {
                for (Object obj : objs) {
                    if (obj != null) {
                        if (isSameList) {
                            TagObject object1 = new TagObject(name, namespace, 0, 0);
                            fillTag(object1, obj, parent);
                            list.add(object1);
                        } else {
                            TagObject object1 = new TagObject(itemName, namespace, 0, 0);
                            fillTag(object1, obj, parent);
                            root.addSubTag(object1);
                        }
                    }
                }
            }
        }
        return list;
    }

    private ArrayList<TagObject> map(Field field, Object object, Object parent) throws Exception {
        XmlElement xmlElement = field.getAnnotation(XmlElement.class);
        String name = xmlElement == null ? field.getName() : xmlElement.value();
        String namespace = xmlElement == null ? null : xmlElement.namespace();
        String itemName = xmlElement == null ? XmlElement.ITEM : xmlElement.listItem();
        String keyName = XmlElement.KEY;
        String valueName = XmlElement.VALUE;
        boolean isSameList = mOptions.isSameAsList();
        ArrayList<TagObject> list = new ArrayList<TagObject>();
        TagObject root = new TagObject(name, namespace, 0, 0);
        if (!isSameList) {
            list.add(root);
        }

        Object set = on(object.getClass()).call(object, "entrySet");
        if (set instanceof Set) {
            Set<Map.Entry<?, ?>> sets = (Set<Map.Entry<?, ?>>) set;
            for (Map.Entry<?, ?> e : sets) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (k == null) {
                    continue;
                }
                TagObject data;
                if (isSameList) {
                    data = new TagObject(name, namespace, 0, 0);
                } else {
                    data = new TagObject(itemName, namespace, 0, 0);
                }
                TagObject key = new TagObject(keyName, namespace, 0, 0);
                fillTag(key, k, parent);
                data.addSubTag(key);
                TagObject value = new TagObject(valueName, namespace, 0, 1);
                fillTag(value, v, parent);
                data.addSubTag(value);
                if (isSameList) {
                    list.add(data);
                } else {
                    root.addSubTag(data);
                }
            }
        }
        return list;
    }
    //endregion

}
