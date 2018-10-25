package net.kk.xml;

import net.kk.xml.adapter.XmlTextAdapter;
import net.kk.xml.annotations.XmlElement;
import net.kk.xml.bean.AttributeObject;
import net.kk.xml.bean.TagObject;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlReader extends XmlCore {
    protected XmlPullParser xmlParser;

    /***
     * @see org.xmlpull.v1.XmlPullParserFactory newInstance().newPullParser()
     */
    public XmlReader(XmlPullParser xmlParser, XmlOptions options) {
        super(options);
        this.xmlParser = xmlParser;
    }

    //region api
    public <T> T fromXml(Class<T> pClass, String xml) throws Exception {
        T t = null;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes("utf-8"));
        try {
            t = fromInputStream(pClass, inputStream, "utf-8");
        } finally {
            inputStream.close();
        }
        return t;
    }

    public <T> T fromFile(Class<T> pClass, String file, String encoding) throws Exception {
        T t = null;
        FileInputStream inputStream = new FileInputStream(file);
        try {
            t = fromInputStream(pClass, inputStream, encoding);
        } finally {
            inputStream.close();
        }
        return t;
    }

    public <T> T fromInputStream(Class<T> pClass, InputStream inputStream, String encoding) throws Exception {
        TagObject root = parseTags(inputStream, encoding);
        return toObject(pClass, root, null, null);
    }

    public <T> T fromTag(Class<T> pClass, TagObject object) throws Exception {
        return toObject(pClass, object, null, null);
    }

    private <T> T toObject(Class<T> pClass, TagObject root, T t, Object parent) throws Exception {
        if (t == null) {
            t = create(pClass, parent);
        }
        if (t == null) {
            return null;
        }
        Reflect reflect = on(t);
        Collection<Field> fields = reflect.getFields();
        List<AttributeObject> attributeObjects = root.getAttributes();
        List<AttributeObject> attributes = null;
        List<TagObject> subTags = null;
        if (attributeObjects != null && !attributeObjects.isEmpty()) {
            attributes = new ArrayList<>();
            attributes.addAll(attributeObjects);
        }
        List<TagObject> subTagObjects = root.getSubTags();
        if (subTagObjects != null && !subTagObjects.isEmpty()) {
            subTags = new ArrayList<>();
            subTags.addAll(subTagObjects);
        }
        //text
        if (!isEmtry(root.getText())) {
            if (reflect.isNormal()) {
                return (T) ReflectUtils.wrapperValue(pClass, root.getText(), mOptions);
            }
            for (Field field : fields) {
                if (isXmlText(field)) {
                    setField(reflect, t, field, root.getText());
                    break;
                }
            }
        }
        boolean isattribute;
        List<TagObject> sublist = new ArrayList<>();
        boolean isList = false;
        AttributeObject attr = null;
        for (Field field : fields) {
            //attributes
            isattribute = false;
            if (attributes != null) {
                attr = null;
                for (AttributeObject attributeObject : attributes) {
                    if (matchAttribute(field, attributeObject.getName(), attributeObject.getNamespace())) {
                        attr = attributeObject;
                        isattribute = true;
                        break;
                    }
                }
                if (attr != null) {
                    setField(reflect, t, field, attr.getValue());
                    attributes.remove(attr);
                }
            }
            if (isattribute) {
                continue;
            }
            //sub tag
            if (subTags != null) {
                isList = false;
                boolean isSameList=mOptions.isSameAsList();
                for (TagObject subtag : subTags) {
                    if (matchTag(field, subtag.getName())) {
                        if (isList) {
                            sublist.add(subtag);
                        } else if (field.getType().isArray()
                                || Collection.class.isAssignableFrom(field.getType())
                                || Map.class.isAssignableFrom(field.getType())) {
                            sublist.add(subtag);
                            isList = true;
                            if(!isSameList){
                                break;
                            }
                        } else {
                            subTags.remove(subtag);
                            setField(reflect, t, field, subtag);
                            break;
                        }
                    }
                }
                if (isList) {
                    setFieldList(reflect, t, field, sublist);
                    for (TagObject object : sublist) {
                        subTags.remove(object);
                    }
                    sublist.clear();
                }
            }
        }
        return t;
    }

    //endregion

    //region set field
    private void setFieldList(Reflect reflect, Object parent, Field field, List<TagObject> xmlObjects) throws Exception {
        Class<?> pClass = field.getType();
        Object object = field.get(parent);
        boolean org = object != null;
        if (pClass.isArray()) {
            object = array(xmlObjects, pClass, object, parent);
        } else if (Collection.class.isAssignableFrom(pClass)) {
            object = list(xmlObjects, pClass, object, parent, ReflectUtils.getListClass(field));
        } else if (Map.class.isAssignableFrom(pClass)) {
            object = map(xmlObjects, pClass, object, parent, ReflectUtils.getMapClass(field));
        }
        if (!org && object != null) {
            try {
                reflect.call(parent, "set" + field.getName(), object);
            } catch (Exception e) {
                field.set(parent, object);
            }
        }
    }

    private <T> T array(List<TagObject> _xmlObjects, Class<T> pClass, Object object, Object parent) throws Exception {
        if (_xmlObjects == null || _xmlObjects.size() == 0) {
            return null;
        }
        Class<?> sc = pClass.getComponentType();
        List<TagObject> xmlObjects;
        if (!mOptions.isSameAsList()) {
            xmlObjects = _xmlObjects.get(0).getSubTags();
        } else {
            xmlObjects = _xmlObjects;
        }
        int count = xmlObjects.size();
        T t;
        if (object != null) {
            t = (T) object;
            //长度不够
        } else {
            t = (T) Array.newInstance(sc, count);
        }
        int integer = on(pClass).get(t, "length", -1);
        if (integer < count) {
            t = (T) Array.newInstance(sc, count);
        }
        for (int i = 0; i < count; i++) {
            TagObject xmlObject = xmlObjects.get(i);
            Object o = toObject(sc, xmlObject, null, parent);
            if (o != null) {
                Array.set(t, i, o);
            }
        }
        return t;
    }

    private <T> T map(List<TagObject> _xmlObjects, Class<T> pClass, Object object, Object parent, Class<?>[] subClass)
            throws Exception {
        if (_xmlObjects == null || _xmlObjects.size() == 0 || subClass == null || subClass.length < 2) {
            return null;
        }
        T t;
        if (object == null) {
            t = (T) ReflectUtils.createMap(pClass, subClass[0], subClass[1]);
        } else {
            t = (T) object;
        }
        if (t == null)
            return t;
        List<TagObject> xmlObjects;
        if (!mOptions.isSameAsList()) {
            xmlObjects = _xmlObjects.get(0).getSubTags();
        } else {
            xmlObjects = _xmlObjects;
        }
        for (TagObject xmlObject : xmlObjects) {
//            if (!mOptions.isSameAsList()) {
//                xmlObject = xmlObject.getChild(XmlTag.ITEM);
//            }
            TagObject tk = xmlObject.getChild(XmlElement.KEY);
            Object k = toObject(subClass[0], tk, null, parent);
            if (k != null) {
                TagObject tv = xmlObject.getChild(XmlElement.VALUE);
                Object v = toObject(subClass[1], tv, null, parent);
                on(t).call(t, "put", k, v);
            }
        }
        return t;
    }

    private <T> T list(List<TagObject> _xmlObjects, Class<T> pClass, Object object, Object parent, Class<?> subClass) throws Exception {
        if (_xmlObjects == null || _xmlObjects.size() == 0) {
            return null;
        }
        List<TagObject> xmlObjects;
        if (!mOptions.isSameAsList()) {
            xmlObjects = _xmlObjects.get(0).getSubTags();
        } else {
            xmlObjects = _xmlObjects;
        }
        T t;
        if (object == null) {
            t = (T) ReflectUtils.createCollection(pClass, subClass);
        } else {
            t = (T) object;
        }
        if (t != null) {
            // 多种派生类
            // boolean d = XmlClassSearcher.class.isAssignableFrom(subClass);
            for (TagObject xmlObject : xmlObjects) {
                Object sub = toObject(subClass, xmlObject, null, parent);
                if (sub != null)
                    on(t).call(t, "add", sub);
            }
        }
        return t;
    }


    private void setField(Reflect reflect, Object parent, Field field, TagObject subtag) throws Exception {
        Class<?> pClass = field.getType();
        Object object = null;
        boolean org = false;
        if (!on(field.getType()).isNormal() && field.get(parent) != null) {
            org = true;
            toObject(pClass, subtag, reflect.get(parent, field.getName()), parent);
        } else {
            XmlTextAdapter xmlTextAdapter = getTypeAdapter(pClass);
            if (xmlTextAdapter != null) {
                object = xmlTextAdapter.toObject(pClass, subtag.getText(), parent);
            }
            if (object == null) {
                if (ReflectUtils.isNormal(pClass)) {
                    object = ReflectUtils.wrapperValue(pClass, subtag.getText(), mOptions);
                } else {
                    object = toObject(pClass, subtag, null, parent);
                }
            }
        }

        if (!org && object != null) {
            try {
                reflect.call(parent, "set" + field.getName(), object);
            } catch (Exception e) {
                field.set(parent, object);
            }
        }
    }

    private void setField(Reflect reflect, Object obj, Field field, String text) throws Exception {
        XmlTextAdapter xmlTextAdapter = getTypeAdapter(field.getType());
        Object val = null;
        if (xmlTextAdapter != null) {
            val = xmlTextAdapter.toObject(field.getType(), text, obj);
        } else {
            val = ReflectUtils.wrapperValue(field.getType(), text, mOptions);
        }
        if (val != null) {
            try {
                reflect.call(obj, "set" + field.getName(), val);
            } catch (Exception e) {
                field.set(obj, val);
            }
        }
    }

    //endregion

    //region read xml
//    private void addTag(TagObject tagObject, Map<Integer, List<TagObject>> xmlTagMap) {
//        Integer key = Integer.valueOf(tagObject.getDepth());
//        List<TagObject> tags = xmlTagMap.get(key);
//        if (tags == null) {
//            tags = new ArrayList<>();
//            xmlTagMap.put(key, tags);
//        }
//        tags.add(tagObject);
//    }
    /*
    * <a>
    *     <b>
    *         <c></c>
    *     </b>
    *     <b>
    *         <c></c>
    *     </b>
    * </a>
    * <a>
    *     <b>
    *         <c></c>
    *     </b>
    *     <b>
    *         <c></c>
    *     </b>
    * </a>
    * */

    /**
     * 从流转换为tag对象
     *
     * @param inputStream 输入流
     * @return tag对象
     */
    TagObject parseTags(InputStream inputStream, String encoding) throws IOException, XmlPullParserException {
        Map<Integer, TagObject> xmlParent = new HashMap<>();
        TagObject lastTag = null;
        //当前tag
        TagObject curTag = null;
        String xmlTag = null;
        int curDepth = -1;
        int curIndex = 0;

        if (inputStream != null) {
            xmlParser.setInput(inputStream, encoding == null ? DEF_ENCODING : encoding);
        }
        int evtType = xmlParser.getEventType();
        while (evtType != XmlPullParser.END_DOCUMENT) {
            // 一直循环，直到文档结束
            switch (evtType) {
                case XmlPullParser.START_TAG: {
                    // 属性
                    xmlTag = xmlParser.getName();
                    int depth = xmlParser.getDepth();
                    if (depth != curDepth) {
                        curIndex = 0;
                        lastTag = xmlParent.get(Integer.valueOf(depth - 1));
                    }
                    TagObject tag = new TagObject(xmlTag, xmlParser.getNamespace(), depth, curIndex);
                    curIndex++;
                    xmlParent.put(Integer.valueOf(depth), tag);
                    if (lastTag != null) {
                        lastTag.addSubTag(tag);
                    }
                    //关联上级元素？
                    int count = xmlParser.getAttributeCount();
                    for (int i = 0; i < count; i++) {
                        String np = xmlParser.getAttributeNamespace(i);
                        String k = xmlParser.getAttributeName(i);
                        String v = xmlParser.getAttributeValue(i);
                        tag.addAttribute(new AttributeObject(np, k, v));
                    }
                    curTag = tag;
                }
                break;
                case XmlPullParser.COMMENT: {
                    String text = xmlParser.getPositionDescription();
                    if (curTag != null && curTag.getText() == null) {
                        curTag.addComment(text);
                    }
                }
                break;
                case XmlPullParser.TEXT: {
                    String text = xmlParser.getText();
                    if (curTag != null && curTag.getText() == null) {
                        if (text != null) {
                            int len = text.length();
                            for (int i = 0; i < len; i++) {
                                char c = text.charAt(i);
                                if (c > 32) {
                                    text = text.trim();
                                    curTag.setText(text);
                                }
                            }
                        }
                    }
                }
                break;
                case XmlPullParser.END_TAG:
                    curTag = null;
                    xmlTag = null;
                    // mElement.setType(findTagClass(parent, xmlTag, mElement));
                    break;
            }
            evtType = xmlParser.next();
        }
        return xmlParent.get(1);
    }
    //endregion
}
