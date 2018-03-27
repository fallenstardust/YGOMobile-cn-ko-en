package net.kk.xml.bean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TagObject {
    //tag名字
    private String name;
    //深度
    private int depth;
    //tag的同级位置
    private int pos;
    //innertext
    private String text;
    //Namespace
    private String namespace;
    private List<String> comments;
    private List<AttributeObject> attributes;
    private List<TagObject> subTags;

    public TagObject() {

    }

    public TagObject(String name, String namespace, int depth, int pos) {
        this.name = name;
        this.namespace = namespace;
        this.depth = depth;
        this.pos = pos;
    }

    public void addSubTag(TagObject tagObject) {
        if (tagObject == null) return;
        if (subTags == null) {
            subTags = new ArrayList<>();
        }
        subTags.add(tagObject);
    }

    public void addSubTags(List<TagObject> tagObjects) {
        if (tagObjects == null) return;
        if (subTags == null) {
            subTags = new ArrayList<>();
        }
        subTags.addAll(tagObjects);
    }

    public void addAttribute(AttributeObject attributeObject) {
        if (attributeObject == null) {
            return;
        }
        if (attributes == null) {
            attributes = new ArrayList<>();
        }
        attributes.add(attributeObject);
    }

    public void addComment(String commment) {
        if (commment == null) {
            return;
        }
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(commment);
    }

    public List<TagObject> getSubTags() {
        return subTags;
    }

    public TagObject getChild(String name) {
        if (subTags == null || name == null) return null;
        for (TagObject tagObject : subTags) {
            if (name.equals(tagObject.getName())) {
                return tagObject;
            }
        }
        return null;
    }

    public List<AttributeObject> getAttributes() {
        return attributes;
    }

    public List<String> getComments() {
        return comments;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getPos() {
        return pos;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public String toString() {
        String _text = "TagObject{" +
                "name='" + name + '\'' +
                ", depth=" + depth +
                ", pos=" + pos;
        if (text != null)
            _text += ", text='" + text + '\'';
        if (namespace != null&&namespace.length()>0)
            _text += ", namespace='" + namespace + '\'';
        if (comments != null)
            _text += ", comments=" + comments;
        if (attributes != null)
            _text += ", attributes=" + attributes;
        if (subTags != null)
            _text += ", subTags=" + subTags;
        _text += '}';
        return _text;
    }

    public static final Comparator<TagObject> ASC = new Comparator<TagObject>() {
        @Override
        public int compare(TagObject o1, TagObject o2) {
            if (o1.depth == o2.depth) {
                return o1.pos - o2.pos;
            }
            return o1.depth - o2.depth;
        }
    };
}
