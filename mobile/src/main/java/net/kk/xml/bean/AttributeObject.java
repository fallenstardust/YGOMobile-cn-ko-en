package net.kk.xml.bean;

public class AttributeObject {
    public AttributeObject(String namespace, String name) {
        this(namespace, name, null);
    }

    public AttributeObject(String namespace, String name, String value) {
        this.name = name;
        if (namespace != null && namespace.trim().length() == 0) {
            namespace = null;
        }
        this.namespace = namespace;
        this.value = value;
    }

    private final String name;
    private final String namespace;
    private String value;

    public void setValue(String value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AttributeObject) {
            AttributeObject other = (AttributeObject) o;
            if (other.getName() != null && !other.getName().equals(name)) {
                return false;
            } else if (name != null) {
                return false;
            }
            if (other.getNamespace() != null && !other.getNamespace().equals(name)) {
                return false;
            } else return namespace == null;
        }
        return super.equals(o);
    }

    @Override
    public String toString() {
        String text = "attr{" +
                "name='" + name + '\'';
        if (namespace != null) {
            text += ", namespace='" + namespace + '\'';
        }
        if (value != null) {
            text += ", value='" + value + '\'';
        }
        text += '}';
        return text;
    }
}
